package com.minju.geogdalservice.gis;

import com.minju.geogdalservice.util.GdalInitializer;
import lombok.RequiredArgsConstructor;
import org.gdal.gdal.Band;
import org.gdal.gdal.Dataset;
import org.gdal.gdal.gdal;
import org.gdal.gdalconst.gdalconstConstants;
import org.gdal.osr.CoordinateTransformation;
import org.gdal.osr.SpatialReference;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Polygon;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * GDAL로 래스터를 열어 검수·모델링에 필요한 메타데이터를 추출한다.
 */
@Component
@RequiredArgsConstructor
public class RasterInspector {

    // 통계 계산 시 원본 전체가 아닌 최대 SAMPLE_SIZE x SAMPLE_SIZE 로 다운샘플링해서 읽는다
    private static final int SAMPLE_SIZE = 1024;
    // footprint 경계를 변 하나당 몇 개 점으로 나눠 변환할지 (투영 왜곡 반영)
    private static final int EDGE_DENSIFY = 8;

    private final GdalInitializer gdalInitializer;

    public RasterInfo inspect(Path file) {
        return inspect(file.toString());
    }

    public RasterInfo inspect(String gdalPath) {
        gdalInitializer.requireAvailable();

        Dataset ds = gdal.Open(gdalPath, gdalconstConstants.GA_ReadOnly);
        if (ds == null) {
            throw new IllegalArgumentException("GDAL로 열 수 없는 래스터 파일입니다: " + gdal.GetLastErrorMsg());
        }
        try {
            if (ds.GetRasterCount() == 0) {
                throw new IllegalArgumentException("래스터 밴드가 없는 파일입니다.");
            }
            int width = ds.GetRasterXSize();
            int height = ds.GetRasterYSize();
            Band band = ds.GetRasterBand(1);

            Double[] noDataHolder = new Double[1];
            band.GetNoDataValue(noDataHolder);
            Double noData = noDataHolder[0];

            double[] gt = ds.GetGeoTransform();
            String wkt = ds.GetProjectionRef();

            RasterInfo.RasterInfoBuilder builder = RasterInfo.builder()
                    .width(width)
                    .height(height)
                    .bandCount(ds.GetRasterCount())
                    .dataType(gdal.GetDataTypeName(band.getDataType()))
                    .srsWkt(wkt == null || wkt.isBlank() ? null : wkt)
                    .geoTransform(gt)
                    .pixelSizeX(gt == null ? null : Math.abs(gt[1]))
                    .pixelSizeY(gt == null ? null : Math.abs(gt[5]))
                    .noDataValue(noData);

            fillStatistics(builder, band, width, height, noData);

            RasterInfo partial = builder.build();
            if (partial.hasCrs() && partial.isGeoreferenced()) {
                fillSpatialInfo(builder, wkt, gt, width, height);
            }
            return builder.build();
        } finally {
            ds.delete();
        }
    }

    private void fillSpatialInfo(RasterInfo.RasterInfoBuilder builder, String wkt, double[] gt, int width, int height) {
        SpatialReference srs = GdalSrs.fromWkt(wkt);
        SpatialReference wgs84 = GdalSrs.wgs84();
        CoordinateTransformation ct = CoordinateTransformation.CreateCoordinateTransformation(srs, wgs84);
        try {
            builder.epsg(GdalSrs.identifyEpsg(srs));

            Polygon footprint = footprint(ct, gt, width, height);
            builder.footprint(footprint);

            // 미터 단위 해상도
            double pixelX = Math.abs(gt[1]);
            if (srs.IsGeographic() == 1) {
                double centerLat = footprint.getCentroid().getY();
                builder.resolutionM(pixelX * 111_320 * Math.cos(Math.toRadians(centerLat)));
            } else {
                builder.resolutionM(pixelX * srs.GetLinearUnits());
            }
        } finally {
            if (ct != null) ct.delete();
            srs.delete();
            wgs84.delete();
        }
    }

    // 픽셀 경계를 따라 점을 샘플링한 뒤 WGS84로 변환해 footprint 폴리곤 생성
    private Polygon footprint(CoordinateTransformation ct, double[] gt, int width, int height) {
        double[][] pixelRing = new double[EDGE_DENSIFY * 4 + 1][];
        int idx = 0;
        for (int i = 0; i < EDGE_DENSIFY; i++) pixelRing[idx++] = new double[]{width * i / (double) EDGE_DENSIFY, 0};
        for (int i = 0; i < EDGE_DENSIFY; i++) pixelRing[idx++] = new double[]{width, height * i / (double) EDGE_DENSIFY};
        for (int i = 0; i < EDGE_DENSIFY; i++) pixelRing[idx++] = new double[]{width - width * i / (double) EDGE_DENSIFY, height};
        for (int i = 0; i < EDGE_DENSIFY; i++) pixelRing[idx++] = new double[]{0, height - height * i / (double) EDGE_DENSIFY};
        pixelRing[idx] = pixelRing[0];

        Coordinate[] ring = new Coordinate[pixelRing.length];
        for (int i = 0; i < pixelRing.length; i++) {
            double[] geoX = new double[1];
            double[] geoY = new double[1];
            gdal.ApplyGeoTransform(gt, pixelRing[i][0], pixelRing[i][1], geoX, geoY);
            double[] lonLat = ct.TransformPoint(geoX[0], geoY[0]);
            ring[i] = new Coordinate(lonLat[0], lonLat[1]);
        }
        return GeometryUtils.FACTORY.createPolygon(ring);
    }

    // 다운샘플링한 밴드 1의 NoData 비율과 최소/최대값
    private void fillStatistics(RasterInfo.RasterInfoBuilder builder, Band band, int width, int height, Double noData) {
        int bufW = Math.min(width, SAMPLE_SIZE);
        int bufH = Math.min(height, SAMPLE_SIZE);
        double[] buffer = new double[bufW * bufH];
        int err = band.ReadRaster(0, 0, width, height, bufW, bufH, gdalconstConstants.GDT_Float64, buffer);
        if (err != gdalconstConstants.CE_None) {
            return;
        }

        long noDataCount = 0;
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (double v : buffer) {
            if (Double.isNaN(v) || (noData != null && v == noData)) {
                noDataCount++;
                continue;
            }
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        builder.noDataRatio((double) noDataCount / buffer.length);
        if (noDataCount < buffer.length) {
            builder.minValue(min).maxValue(max);
        }
    }
}
