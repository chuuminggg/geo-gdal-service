package com.minju.geogdalservice.util;

// 위경도 거리 계산 (구면 근사, WGS84 평균 반경 사용)
public final class GeoDistanceUtils {

    public static final double EARTH_RADIUS_METERS = 6_371_008.8;

    private GeoDistanceUtils() {
    }

    // Haversine 공식으로 두 지점 사이의 대원 거리(m) 계산
    public static double haversine(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }

    // 중심점과 반경(m)을 모두 포함하는 경계 사각형 [minLat, maxLat, minLon, maxLon]
    // 극점이나 날짜변경선(±180)에 걸리면 경도 범위를 전체로 확장한다.
    public static double[] boundingBox(double lat, double lon, double radiusMeters) {
        double angularRadius = Math.toDegrees(radiusMeters / EARTH_RADIUS_METERS);
        double minLat = lat - angularRadius;
        double maxLat = lat + angularRadius;

        if (minLat <= -90 || maxLat >= 90) {
            return new double[]{Math.max(minLat, -90), Math.min(maxLat, 90), -180, 180};
        }

        // 해당 위도에서 반경이 차지하는 최대 경도 폭
        double deltaLon = Math.toDegrees(Math.asin(
                Math.min(1.0, Math.sin(radiusMeters / EARTH_RADIUS_METERS) / Math.cos(Math.toRadians(lat)))));
        double minLon = lon - deltaLon;
        double maxLon = lon + deltaLon;
        if (minLon < -180 || maxLon > 180) {
            return new double[]{minLat, maxLat, -180, 180};
        }
        return new double[]{minLat, maxLat, minLon, maxLon};
    }
}
