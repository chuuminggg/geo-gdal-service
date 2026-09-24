package com.minju.geogdalservice.gis;

import org.locationtech.jts.geom.*;

public final class GeometryUtils {

    public static final int WGS84 = 4326;
    public static final GeometryFactory FACTORY = new GeometryFactory(new PrecisionModel(), WGS84);

    private static final double EARTH_RADIUS_M = 6_371_008.8;

    // 대한민국 영역(제주·울릉·독도 포함) 대략적인 경위도 범위
    public static final Envelope KOREA_BOUNDS = new Envelope(124.0, 132.0, 33.0, 39.0);

    private GeometryUtils() {
    }

    public static Point point(double lon, double lat) {
        return FACTORY.createPoint(new Coordinate(lon, lat));
    }

    public static Polygon envelope(double minLon, double minLat, double maxLon, double maxLat) {
        if (minLon > maxLon || minLat > maxLat) {
            throw new IllegalArgumentException("bbox 범위가 올바르지 않습니다. (min <= max)");
        }
        return (Polygon) FACTORY.toGeometry(new Envelope(minLon, maxLon, minLat, maxLat));
    }

    public static void validateLonLat(double lon, double lat) {
        if (lon < -180 || lon > 180 || lat < -90 || lat > 90) {
            throw new IllegalArgumentException("경위도 범위를 벗어났습니다: lon=%s, lat=%s".formatted(lon, lat));
        }
    }

    // 두 경위도 좌표 간 대원거리 (미터)
    public static double haversine(double lon1, double lat1, double lon2, double lat2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_RADIUS_M * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }

    // LineString(경위도) 길이 (미터)
    public static double lengthMeters(LineString line) {
        Coordinate[] cs = line.getCoordinates();
        double sum = 0;
        for (int i = 1; i < cs.length; i++) {
            sum += haversine(cs[i - 1].x, cs[i - 1].y, cs[i].x, cs[i].y);
        }
        return sum;
    }
}
