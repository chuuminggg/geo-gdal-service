package com.minju.geogdalservice.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 테스트용 GeoJSON 생성
 */
public final class TestGeoJson {

    private TestGeoJson() {
    }

    /**
     * n x n 격자 도로망. (lon0, lat0) 에서 시작해 step 도 간격으로 교차로가 있고
     * 교차로 사이마다 링크 하나 (가로 n*(n-1) + 세로 n*(n-1) 개).
     */
    public static String gridRoads(double lon0, double lat0, double step, int n) {
        List<String> features = new ArrayList<>();
        int id = 1;
        for (int row = 0; row < n; row++) {
            for (int col = 0; col < n; col++) {
                double lon = lon0 + col * step;
                double lat = lat0 + row * step;
                if (col < n - 1) {
                    features.add(line(id++, lon, lat, lon + step, lat, "residential", 30));
                }
                if (row < n - 1) {
                    features.add(line(id++, lon, lat, lon, lat + step, "primary", 50));
                }
            }
        }
        return collection(features);
    }

    public static String line(int id, double lon1, double lat1, double lon2, double lat2, String highway, int maxSpeed) {
        return String.format(Locale.ROOT,
                "{\"type\":\"Feature\",\"properties\":{\"link_id\":\"%d\",\"highway\":\"%s\",\"maxspeed\":\"%d\"},"
                        + "\"geometry\":{\"type\":\"LineString\",\"coordinates\":[[%.7f,%.7f],[%.7f,%.7f]]}}",
                id, highway, maxSpeed, lon1, lat1, lon2, lat2);
    }

    public static String point(String name, String category, double lon, double lat) {
        return String.format(Locale.ROOT,
                "{\"type\":\"Feature\",\"properties\":{\"name\":\"%s\",\"category\":\"%s\"},"
                        + "\"geometry\":{\"type\":\"Point\",\"coordinates\":[%.7f,%.7f]}}",
                name, category, lon, lat);
    }

    public static String collection(List<String> features) {
        return "{\"type\":\"FeatureCollection\",\"features\":[" + String.join(",", features) + "]}";
    }
}
