package com.minju.geogdalservice.support;

import com.minju.geogdalservice.util.GdalInitializer;
import org.gdal.gdal.Band;
import org.gdal.gdal.Dataset;
import org.gdal.gdal.gdal;
import org.gdal.gdalconst.gdalconstConstants;
import org.gdal.ogr.ogr;
import org.gdal.osr.SpatialReference;

import java.nio.file.Path;
import java.util.function.IntBinaryOperator;

/**
 * GDAL 네이티브 라이브러리가 있는 환경(docker/gdal 이미지)에서만 실행되는 테스트용 지원 클래스.
 * 사용: @EnabledIf("com.minju.geogdalservice.support.GdalTestSupport#gdalAvailable")
 */
public final class GdalTestSupport {

    private static final boolean AVAILABLE = load();

    private GdalTestSupport() {
    }

    private static boolean load() {
        try {
            gdal.AllRegister();
            ogr.RegisterAll();
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    public static boolean gdalAvailable() {
        return AVAILABLE;
    }

    public static GdalInitializer initializer() {
        GdalInitializer initializer = new GdalInitializer();
        initializer.init();
        return initializer;
    }

    /**
     * 테스트용 단일 밴드 Float32 GeoTIFF 생성.
     *
     * @param epsg   null 이면 좌표계 없이 생성
     * @param values (col, row) -> 픽셀 값
     */
    public static Path createGeoTiff(Path path, Integer epsg, double originX, double originY, double pixelSize,
                                     int width, int height, IntBinaryOperator values, Double noData) {
        Dataset ds = gdal.GetDriverByName("GTiff")
                .Create(path.toString(), width, height, 1, gdalconstConstants.GDT_Float32);
        ds.SetGeoTransform(new double[]{originX, pixelSize, 0, originY, 0, -pixelSize});
        if (epsg != null) {
            SpatialReference srs = new SpatialReference();
            srs.ImportFromEPSG(epsg);
            ds.SetProjection(srs.ExportToWkt());
        }
        Band band = ds.GetRasterBand(1);
        if (noData != null) {
            band.SetNoDataValue(noData);
        }
        double[] data = new double[width * height];
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                data[row * width + col] = values.applyAsInt(col, row);
            }
        }
        band.WriteRaster(0, 0, width, height, data);
        ds.FlushCache();
        ds.delete();
        return path;
    }
}
