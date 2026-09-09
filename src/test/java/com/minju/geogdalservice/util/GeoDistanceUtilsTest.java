package com.minju.geogdalservice.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class GeoDistanceUtilsTest {

    private static final double ONE_DEGREE_METERS = GeoDistanceUtils.EARTH_RADIUS_METERS * Math.PI / 180;

    @Test
    void haversine_sameMeridianOneDegree() {
        assertThat(GeoDistanceUtils.haversine(37.0, 127.0, 38.0, 127.0))
                .isCloseTo(ONE_DEGREE_METERS, within(0.01));
    }

    @Test
    void haversine_equatorAcrossAntimeridian() {
        assertThat(GeoDistanceUtils.haversine(0.0, 179.5, 0.0, -179.5))
                .isCloseTo(ONE_DEGREE_METERS, within(0.01));
    }

    @Test
    void haversine_samePointIsZero() {
        assertThat(GeoDistanceUtils.haversine(37.5665, 126.9780, 37.5665, 126.9780)).isZero();
    }

    @Test
    void boundingBox_containsPointsAtRadiusInEveryDirection() {
        double lat = 37.5665, lon = 126.9780, radius = 5_000;
        double[] box = GeoDistanceUtils.boundingBox(lat, lon, radius);

        // 중심에서 radius 거리의 원 위의 점들이 모두 경계 사각형 안에 있어야 한다
        for (int bearing = 0; bearing < 360; bearing += 5) {
            double[] p = destination(lat, lon, bearing, radius * 0.9999);
            assertThat(p[0]).isBetween(box[0], box[1]);
            assertThat(p[1]).isBetween(box[2], box[3]);
        }
        assertThat(box[1] - box[0]).isLessThan(0.1);
        assertThat(box[3] - box[2]).isLessThan(0.15);
    }

    @Test
    void boundingBox_expandsLongitudeNearAntimeridianAndPoles() {
        double[] antimeridian = GeoDistanceUtils.boundingBox(0, 179.99, 10_000);
        assertThat(antimeridian[2]).isEqualTo(-180);
        assertThat(antimeridian[3]).isEqualTo(180);

        double[] polar = GeoDistanceUtils.boundingBox(89.99, 0, 10_000);
        assertThat(polar[1]).isEqualTo(90);
        assertThat(polar[2]).isEqualTo(-180);
        assertThat(polar[3]).isEqualTo(180);
    }

    // 시작점에서 방위각/거리만큼 이동한 지점 (구면)
    private static double[] destination(double lat, double lon, double bearingDeg, double distance) {
        double delta = distance / GeoDistanceUtils.EARTH_RADIUS_METERS;
        double theta = Math.toRadians(bearingDeg);
        double phi1 = Math.toRadians(lat);
        double lambda1 = Math.toRadians(lon);
        double phi2 = Math.asin(Math.sin(phi1) * Math.cos(delta) + Math.cos(phi1) * Math.sin(delta) * Math.cos(theta));
        double lambda2 = lambda1 + Math.atan2(Math.sin(theta) * Math.sin(delta) * Math.cos(phi1),
                Math.cos(delta) - Math.sin(phi1) * Math.sin(phi2));
        return new double[]{Math.toDegrees(phi2), Math.toDegrees(lambda2)};
    }
}
