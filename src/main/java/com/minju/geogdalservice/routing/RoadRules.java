package com.minju.geogdalservice.routing;

import java.util.Locale;
import java.util.Set;

/**
 * 도로 등급별 통행 가능 여부와 기본 속도.
 * OSM highway 태그와 국가표준노드링크 ROAD_RANK 코드(101~108)를 모두 지원한다.
 */
public final class RoadRules {

    private static final Set<String> CAR_FORBIDDEN = Set.of(
            "footway", "pedestrian", "path", "steps", "cycleway", "corridor", "bridleway");
    private static final Set<String> PEDESTRIAN_FORBIDDEN = Set.of(
            "motorway", "motorway_link", "trunk", "trunk_link",
            "101", "102");   // 고속국도, 도시고속도로
    private static final Set<String> BIKE_FORBIDDEN_EXTRA = Set.of("steps");

    private RoadRules() {
    }

    public static boolean carAllowed(String roadClass) {
        return !CAR_FORBIDDEN.contains(normalize(roadClass));
    }

    public static boolean walkAllowed(String roadClass) {
        return !PEDESTRIAN_FORBIDDEN.contains(normalize(roadClass));
    }

    public static boolean bikeAllowed(String roadClass) {
        String rc = normalize(roadClass);
        return !PEDESTRIAN_FORBIDDEN.contains(rc) && !BIKE_FORBIDDEN_EXTRA.contains(rc);
    }

    /**
     * 차량 주행 속도(km/h): 제한속도가 있으면 제한속도, 없으면 도로 등급별 기본값
     */
    public static int carSpeedKph(String roadClass, int maxSpeedKph) {
        if (maxSpeedKph > 0) {
            return maxSpeedKph;
        }
        return switch (normalize(roadClass)) {
            case "motorway", "101" -> 100;
            case "trunk", "102" -> 80;
            case "motorway_link", "primary", "103" -> 60;
            case "trunk_link", "secondary", "104" -> 50;
            case "tertiary", "105", "106" -> 40;
            case "residential", "unclassified", "107" -> 30;
            case "living_street", "service", "track", "108" -> 20;
            default -> 30;
        };
    }

    private static String normalize(String roadClass) {
        return roadClass == null ? "" : roadClass.trim().toLowerCase(Locale.ROOT);
    }
}
