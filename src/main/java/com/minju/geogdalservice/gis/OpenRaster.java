package com.minju.geogdalservice.gis;

import com.minju.geogdalservice.common.exception.StorageException;
import org.gdal.gdal.Band;
import org.gdal.gdal.Dataset;
import org.gdal.gdal.gdal;
import org.gdal.gdalconst.gdalconstConstants;
import org.gdal.osr.CoordinateTransformation;
import org.gdal.osr.SpatialReference;

/**
 * 열려 있는 래스터 핸들. 경위도(EPSG:4326) 좌표로 픽셀 값을 읽는다.
 *
 * GDAL Dataset 은 스레드 안전하지 않으므로 요청(스레드)마다 열고 try-with-resources 로 닫는다.
 * 같은 요청 안에서 여러 좌표를 조회할 때는 한 번만 열어서 재사용한다 (배치 조회, 경로 고도 샘플링).
 */
public class OpenRaster implements AutoCloseable {

    private final Dataset dataset;
    private final double[] inverseGeoTransform;
    private final SpatialReference wgs84;
    private final SpatialReference rasterSrs;
    private final CoordinateTransformation toRaster;   // WGS84 -> 래스터 좌표계 (같으면 null)
    private final Double[] noData;
    private final int width;
    private final int height;

    private OpenRaster(Dataset dataset) {
        this.dataset = dataset;
        this.width = dataset.GetRasterXSize();
        this.height = dataset.GetRasterYSize();

        this.inverseGeoTransform = gdal.InvGeoTransform(dataset.GetGeoTransform());
        if (inverseGeoTransform == null) {
            throw new IllegalStateException("GeoTransform 역변환을 계산할 수 없는 래스터입니다.");
        }

        this.wgs84 = GdalSrs.wgs84();
        this.rasterSrs = GdalSrs.fromWkt(dataset.GetProjectionRef());
        this.toRaster = rasterSrs.IsSame(wgs84) == 1
                ? null
                : CoordinateTransformation.CreateCoordinateTransformation(wgs84, rasterSrs);

        this.noData = new Double[dataset.GetRasterCount()];
        for (int b = 0; b < noData.length; b++) {
            Double[] holder = new Double[1];
            dataset.GetRasterBand(b + 1).GetNoDataValue(holder);
            noData[b] = holder[0];
        }
    }

    public static OpenRaster open(String gdalPath) {
        Dataset dataset = gdal.Open(gdalPath, gdalconstConstants.GA_ReadOnly);
        if (dataset == null) {
            throw new StorageException("래스터를 열 수 없습니다: " + gdalPath + " (" + gdal.GetLastErrorMsg() + ")");
        }
        try {
            return new OpenRaster(dataset);
        } catch (RuntimeException e) {
            dataset.delete();
            throw e;
        }
    }

    public int bandCount() {
        return noData.length;
    }

    /**
     * 경위도 -> 픽셀 좌표 (실수). 픽셀 (0,0) 의 좌상단 모서리가 (0.0, 0.0)
     */
    public double[] toPixel(double lon, double lat) {
        double x = lon;
        double y = lat;
        if (toRaster != null) {
            double[] p = toRaster.TransformPoint(lon, lat);
            x = p[0];
            y = p[1];
        }
        double[] inv = inverseGeoTransform;
        return new double[]{
                inv[0] + inv[1] * x + inv[2] * y,
                inv[3] + inv[4] * x + inv[5] * y
        };
    }

    public boolean contains(double lon, double lat) {
        double[] px = toPixel(lon, lat);
        return px[0] >= 0 && px[1] >= 0 && px[0] < width && px[1] < height;
    }

    /**
     * 좌표가 속한 픽셀의 모든 밴드 값 (범위 밖이면 null, NoData 인 밴드는 null)
     */
    public Double[] valuesAt(double lon, double lat) {
        double[] px = toPixel(lon, lat);
        int col = (int) Math.floor(px[0]);
        int row = (int) Math.floor(px[1]);
        if (col < 0 || row < 0 || col >= width || row >= height) {
            return null;
        }
        Double[] values = new Double[noData.length];
        double[] buffer = new double[1];
        for (int b = 0; b < noData.length; b++) {
            dataset.GetRasterBand(b + 1).ReadRaster(col, row, 1, 1, gdalconstConstants.GDT_Float64, buffer);
            values[b] = isNoData(b, buffer[0]) ? null : buffer[0];
        }
        return values;
    }

    /**
     * 밴드 1 값을 주변 4개 픽셀 중심으로 쌍선형 보간 (DEM 고도 조회용).
     * 보간에 쓰일 픽셀 중 NoData 가 있으면 가장 가까운 픽셀 값을 사용한다.
     */
    public Double interpolate(double lon, double lat) {
        double[] px = toPixel(lon, lat);
        if (px[0] < 0 || px[1] < 0 || px[0] >= width || px[1] >= height) {
            return null;
        }
        if (width < 2 || height < 2) {
            return nearest(px);
        }
        // 픽셀 중심 기준 좌표
        double fx = px[0] - 0.5;
        double fy = px[1] - 0.5;
        int x0 = clamp((int) Math.floor(fx), 0, width - 2);
        int y0 = clamp((int) Math.floor(fy), 0, height - 2);
        double dx = clamp(fx - x0, 0, 1);
        double dy = clamp(fy - y0, 0, 1);

        double[] w = new double[4];
        Band band = dataset.GetRasterBand(1);
        band.ReadRaster(x0, y0, 2, 2, gdalconstConstants.GDT_Float64, w);
        for (double v : w) {
            if (isNoData(0, v)) {
                return nearest(px);
            }
        }
        double top = w[0] * (1 - dx) + w[1] * dx;
        double bottom = w[2] * (1 - dx) + w[3] * dx;
        return top * (1 - dy) + bottom * dy;
    }

    private Double nearest(double[] px) {
        int col = clamp((int) Math.floor(px[0]), 0, width - 1);
        int row = clamp((int) Math.floor(px[1]), 0, height - 1);
        double[] buffer = new double[1];
        dataset.GetRasterBand(1).ReadRaster(col, row, 1, 1, gdalconstConstants.GDT_Float64, buffer);
        return isNoData(0, buffer[0]) ? null : buffer[0];
    }

    private boolean isNoData(int band, double v) {
        return Double.isNaN(v) || (noData[band] != null && v == noData[band]);
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    @Override
    public void close() {
        if (toRaster != null) toRaster.delete();
        rasterSrs.delete();
        wgs84.delete();
        dataset.delete();
    }
}
