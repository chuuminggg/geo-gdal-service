package com.minju.geogdalservice.gis;

import org.gdal.osr.SpatialReference;
import org.gdal.osr.osrConstants;

/**
 * GDAL SpatialReference 생성 헬퍼.
 *
 * GDAL 3부터 EPSG:4326 등은 권위(authority) 축 순서(위도, 경도)를 따르므로
 * 항상 OAMS_TRADITIONAL_GIS_ORDER 를 지정해 (x=경도, y=위도) 순서로 통일한다.
 */
public final class GdalSrs {

    private GdalSrs() {
    }

    public static SpatialReference fromEpsg(int epsg) {
        SpatialReference srs = new SpatialReference();
        if (srs.ImportFromEPSG(epsg) != 0) {
            throw new IllegalArgumentException("지원하지 않는 EPSG 코드입니다: " + epsg);
        }
        srs.SetAxisMappingStrategy(osrConstants.OAMS_TRADITIONAL_GIS_ORDER);
        return srs;
    }

    public static SpatialReference fromWkt(String wkt) {
        SpatialReference srs = new SpatialReference();
        if (srs.ImportFromWkt(wkt) != 0) {
            throw new IllegalArgumentException("좌표계(WKT)를 해석할 수 없습니다.");
        }
        srs.SetAxisMappingStrategy(osrConstants.OAMS_TRADITIONAL_GIS_ORDER);
        return srs;
    }

    public static SpatialReference wgs84() {
        return fromEpsg(GeometryUtils.WGS84);
    }

    // WKT에서 EPSG 코드 식별 (식별 불가 시 null)
    public static Integer identifyEpsg(SpatialReference srs) {
        String code = srs.GetAuthorityCode(null);
        if (code == null) {
            SpatialReference clone = srs.Clone();
            if (clone.AutoIdentifyEPSG() == 0) {
                code = clone.GetAuthorityCode(null);
            }
        }
        try {
            return code == null ? null : Integer.valueOf(code);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
