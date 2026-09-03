package com.minju.geogdalservice.util;

import com.minju.geogdalservice.common.exception.GeoServiceException;
import com.minju.geogdalservice.config.GdalInitializer;
import com.minju.geogdalservice.service.RasterStorageService;
import lombok.RequiredArgsConstructor;
import org.gdal.gdal.Band;
import org.gdal.gdal.Dataset;
import org.gdal.gdal.gdal;
import org.gdal.gdalconst.gdalconst;
import org.gdal.osr.CoordinateTransformation;
import org.gdal.osr.SpatialReference;
import org.gdal.osr.osr;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.function.BiFunction;

// GDAL Dataset 열기/닫기, 좌표 → 픽셀 변환, 밴드 값 읽기 공통 로직
@Component
@RequiredArgsConstructor
public class GdalRasterSupport {

    public static final String WGS84 = "EPSG:4326";

    private final GdalInitializer gdalInitializer;
    private final RasterStorageService rasterStorageService;

    // 파일을 읽기 전용으로 열고, 작업이 끝나면 반드시 Dataset을 닫는다.
    public <T> T withDataset(String fileName, BiFunction<Path, Dataset, T> action) {
        gdalInitializer.ensureAvailable();
        Path path = rasterStorageService.resolveExisting(fileName);

        Dataset dataset;
        try {
            dataset = gdal.Open(path.toString(), gdalconst.GA_ReadOnly);
        } catch (RuntimeException e) {
            throw new GeoServiceException(HttpStatus.UNPROCESSABLE_ENTITY, "Failed to open raster: " + fileName, e);
        }
        if (dataset == null) {
            throw new GeoServiceException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Failed to open raster: " + fileName + " (" + gdal.GetLastErrorMsg() + ")");
        }

        try {
            return action.apply(path, dataset);
        } finally {
            dataset.delete();
        }
    }

    // "EPSG:4326", WKT, PROJ 문자열 등 → SpatialReference (x=경도, y=위도 순서)
    public SpatialReference createSpatialReference(String userInput) {
        gdalInitializer.ensureAvailable();
        if (userInput == null || userInput.isBlank()) {
            throw GeoServiceException.badRequest("CRS is required");
        }
        SpatialReference srs = new SpatialReference();
        try {
            srs.SetFromUserInput(userInput.trim());
        } catch (RuntimeException e) {
            throw GeoServiceException.badRequest("Invalid CRS: " + userInput);
        }
        srs.SetAxisMappingStrategy(osr.OAMS_TRADITIONAL_GIS_ORDER);
        return srs;
    }

    public CoordinateTransformation createTransformation(SpatialReference source, SpatialReference target) {
        try {
            return CoordinateTransformation.CreateCoordinateTransformation(source, target);
        } catch (RuntimeException e) {
            throw GeoServiceException.badRequest("Cannot create coordinate transformation: " + e.getMessage());
        }
    }

    public SpatialReference getSpatialReference(Dataset dataset) {
        SpatialReference srs = dataset.GetSpatialRef();
        if (srs == null) {
            throw new GeoServiceException(HttpStatus.UNPROCESSABLE_ENTITY, "Raster has no spatial reference (CRS)");
        }
        srs.SetAxisMappingStrategy(osr.OAMS_TRADITIONAL_GIS_ORDER);
        return srs;
    }

    // WGS84 위경도 → 래스터 픽셀 좌표 (실수, 좌상단 모서리 = 0,0)
    public PixelPosition toPixelPosition(Dataset dataset, double lon, double lat) {
        return createPixelLocator(dataset).locate(lon, lat);
    }

    // 여러 좌표를 조회할 때 좌표 변환 객체를 재사용하기 위한 Locator
    public PixelLocator createPixelLocator(Dataset dataset) {
        SpatialReference rasterSrs = getSpatialReference(dataset);
        CoordinateTransformation transformation = createTransformation(createSpatialReference(WGS84), rasterSrs);
        double[] inverse = gdal.InvGeoTransform(dataset.GetGeoTransform());
        if (inverse == null) {
            throw new GeoServiceException(HttpStatus.UNPROCESSABLE_ENTITY, "Raster geotransform is not invertible");
        }
        int width = dataset.GetRasterXSize();
        int height = dataset.GetRasterYSize();

        return (lon, lat) -> {
            double[] projected;
            try {
                projected = transformation.TransformPoint(lon, lat);
            } catch (RuntimeException e) {
                throw GeoServiceException.badRequest("Failed to transform coordinate (lat=" + lat + ", lon=" + lon + ")");
            }
            double pixelX = inverse[0] + inverse[1] * projected[0] + inverse[2] * projected[1];
            double pixelY = inverse[3] + inverse[4] * projected[0] + inverse[5] * projected[1];
            return new PixelPosition(pixelX, pixelY, width, height);
        };
    }

    public double readRawValue(Band band, int col, int row) {
        double[] buffer = new double[1];
        band.ReadRaster(col, row, 1, 1, buffer);
        return buffer[0];
    }

    public boolean isNoData(Band band, double value) {
        Double[] noData = new Double[1];
        band.GetNoDataValue(noData);
        if (Double.isNaN(value)) {
            return true;
        }
        return noData[0] != null && Double.compare(noData[0], value) == 0;
    }

    // 밴드에 scale/offset이 정의되어 있으면 실제 값으로 변환
    public double applyScaleOffset(Band band, double value) {
        Double[] scale = new Double[1];
        Double[] offset = new Double[1];
        band.GetScale(scale);
        band.GetOffset(offset);
        double s = scale[0] != null ? scale[0] : 1.0;
        double o = offset[0] != null ? offset[0] : 0.0;
        return value * s + o;
    }

    @FunctionalInterface
    public interface PixelLocator {
        PixelPosition locate(double lon, double lat);
    }

    public record PixelPosition(double x, double y, int width, int height) {

        public int col() {
            return (int) Math.floor(x);
        }

        public int row() {
            return (int) Math.floor(y);
        }

        public boolean isInside() {
            return x >= 0 && y >= 0 && col() < width && row() < height;
        }
    }
}
