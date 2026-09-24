package com.minju.geogdalservice.gis;

import lombok.Builder;
import org.locationtech.jts.geom.Polygon;

/**
 * GDAL로 읽은 래스터 정보. GDAL 객체와 분리된 순수 값 객체라서
 * 검수 규칙을 GDAL 네이티브 라이브러리 없이 단위 테스트할 수 있다.
 */
@Builder
public record RasterInfo(
        int width,
        int height,
        int bandCount,
        String dataType,
        Integer epsg,
        String srsWkt,
        double[] geoTransform,
        Double pixelSizeX,
        Double pixelSizeY,
        Double resolutionM,
        Double noDataValue,
        Double noDataRatio,
        Double minValue,
        Double maxValue,
        Polygon footprint   // EPSG:4326, CRS/GeoTransform이 없으면 null
) {

    public boolean hasCrs() {
        return srsWkt != null && !srsWkt.isBlank();
    }

    public boolean isGeoreferenced() {
        if (geoTransform == null || geoTransform.length != 6) {
            return false;
        }
        // GDAL 기본값(0,1,0,0,0,1)은 좌표 정보가 없는 이미지
        double[] identity = {0, 1, 0, 0, 0, 1};
        for (int i = 0; i < 6; i++) {
            if (geoTransform[i] != identity[i]) {
                return true;
            }
        }
        return false;
    }
}
