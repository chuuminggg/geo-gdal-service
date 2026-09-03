package com.minju.geogdalservice.support;

import org.gdal.gdal.Band;
import org.gdal.gdal.Dataset;
import org.gdal.gdal.Driver;
import org.gdal.gdal.gdal;
import org.gdal.gdalconst.gdalconst;
import org.gdal.osr.CoordinateTransformation;
import org.gdal.osr.SpatialReference;
import org.gdal.osr.osr;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * GDAL 네이티브 라이브러리가 필요한 통합 테스트 공통 설정.
 * GDAL을 로드할 수 없는 환경에서는 테스트를 건너뛴다.
 *
 * <p>테스트 DEM (EPSG:32652, 10x10, 픽셀 100m, Float32, 단위 m)
 * <ul>
 *   <li>좌상단 원점: (300000, 4200000)</li>
 *   <li>픽셀 값: row * 10 + col</li>
 *   <li>(col 0, row 0) = NoData(-9999)</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class GdalIntegrationTestSupport {

    protected static final String DEM_FILE = "test_dem.tif";
    protected static final int DEM_SIZE = 10;
    protected static final double ORIGIN_X = 300_000;
    protected static final double ORIGIN_Y = 4_200_000;
    protected static final double PIXEL_SIZE = 100;
    protected static final double NO_DATA = -9999;

    private static final Path STORAGE_DIR = createStorageDir();
    private static final boolean GDAL_AVAILABLE = loadGdal();

    @Autowired
    protected MockMvc mockMvc;

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("geo.storage.dir", STORAGE_DIR::toString);
    }

    @BeforeAll
    static void prepareRaster() {
        Assumptions.assumeTrue(GDAL_AVAILABLE, "GDAL native library is not available");
        createDemIfAbsent();
    }

    // 픽셀 좌표(실수) → WGS84 [lon, lat]
    protected static double[] pixelToLonLat(double pixelX, double pixelY) {
        SpatialReference utm = new SpatialReference();
        utm.ImportFromEPSG(32652);
        utm.SetAxisMappingStrategy(osr.OAMS_TRADITIONAL_GIS_ORDER);
        SpatialReference wgs84 = new SpatialReference();
        wgs84.ImportFromEPSG(4326);
        wgs84.SetAxisMappingStrategy(osr.OAMS_TRADITIONAL_GIS_ORDER);

        double[] lonLat = CoordinateTransformation.CreateCoordinateTransformation(utm, wgs84)
                .TransformPoint(ORIGIN_X + pixelX * PIXEL_SIZE, ORIGIN_Y - pixelY * PIXEL_SIZE);
        return new double[]{lonLat[0], lonLat[1]};
    }

    private static synchronized void createDemIfAbsent() {
        Path path = STORAGE_DIR.resolve(DEM_FILE);
        if (Files.exists(path)) {
            return;
        }
        Driver driver = gdal.GetDriverByName("GTiff");
        Dataset dataset = driver.Create(path.toString(), DEM_SIZE, DEM_SIZE, 1, gdalconst.GDT_Float32);
        try {
            dataset.SetGeoTransform(new double[]{ORIGIN_X, PIXEL_SIZE, 0, ORIGIN_Y, 0, -PIXEL_SIZE});
            SpatialReference srs = new SpatialReference();
            srs.ImportFromEPSG(32652);
            dataset.SetSpatialRef(srs);

            Band band = dataset.GetRasterBand(1);
            band.SetNoDataValue(NO_DATA);
            band.SetUnitType("m");
            double[] values = new double[DEM_SIZE * DEM_SIZE];
            for (int row = 0; row < DEM_SIZE; row++) {
                for (int col = 0; col < DEM_SIZE; col++) {
                    values[row * DEM_SIZE + col] = row * 10 + col;
                }
            }
            values[0] = NO_DATA;
            band.WriteRaster(0, 0, DEM_SIZE, DEM_SIZE, values);
            dataset.FlushCache();
        } finally {
            dataset.delete();
        }
    }

    private static Path createStorageDir() {
        try {
            return Files.createTempDirectory("geogdal-test-");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean loadGdal() {
        try {
            gdal.AllRegister();
            gdal.UseExceptions();
            osr.UseExceptions();
            return true;
        } catch (LinkageError e) {
            return false;
        }
    }
}
