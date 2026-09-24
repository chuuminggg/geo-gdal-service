package com.minju.geogdalservice.validation;

import com.minju.geogdalservice.entity.DatasetType;
import com.minju.geogdalservice.gis.VectorData;
import com.minju.geogdalservice.gis.VectorFeature;
import com.minju.geogdalservice.network.RoadLinkData;
import com.minju.geogdalservice.network.RoadNetwork;
import com.minju.geogdalservice.network.RoadNetworkBuilder;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Component;

/**
 * 벡터(도로 네트워크, POI) 검수 규칙 모음
 */
public final class VectorRules {

    private VectorRules() {
    }

    private static String percent(double ratio) {
        return "%.1f%%".formatted(ratio * 100);
    }

    // 데이터 타입에 맞는 geometry 타입인지 (도로: 라인, POI: 포인트)
    abstract static class GeometryTypeRule implements ValidationRule<VectorData> {
        public String code() {
            return "GEOMETRY_TYPE";
        }

        abstract String expected();

        abstract boolean matches(Geometry g);

        public RuleResult validate(VectorData data) {
            if (data.features().isEmpty()) {
                return RuleResult.error(code(), "피처가 없습니다.");
            }
            long mismatched = data.features().stream()
                    .map(VectorFeature::geometry)
                    .filter(g -> !matches(g))
                    .count();
            if (mismatched == data.features().size()) {
                return RuleResult.error(code(), "%s 타입 피처가 없습니다.".formatted(expected()));
            }
            if (mismatched > 0) {
                return RuleResult.warn(code(), "%s 가 아닌 피처 %d건은 적재에서 제외됩니다.".formatted(expected(), mismatched));
            }
            return RuleResult.pass(code(), "모든 피처(%d건)가 %s 입니다.".formatted(data.features().size(), expected()));
        }
    }

    @Component
    public static class RoadGeometryTypeRule extends GeometryTypeRule {
        public boolean supports(DatasetType type) {
            return type == DatasetType.ROAD_NETWORK;
        }

        String expected() {
            return "LineString";
        }

        boolean matches(Geometry g) {
            return g instanceof LineString || g instanceof MultiLineString;
        }
    }

    @Component
    public static class PoiGeometryTypeRule extends GeometryTypeRule {
        public boolean supports(DatasetType type) {
            return type == DatasetType.POI;
        }

        String expected() {
            return "Point";
        }

        boolean matches(Geometry g) {
            return g instanceof Point;
        }
    }

    // geometry 유효성 (자기교차, 빈 geometry 등). 1% 이하는 제외 후 진행, 초과 시 불합격
    @Component
    public static class GeometryValidityRule implements ValidationRule<VectorData> {
        static final double ERROR_RATIO = 0.01;

        public String code() {
            return "GEOMETRY_VALID";
        }

        public boolean supports(DatasetType type) {
            return type == DatasetType.ROAD_NETWORK || type == DatasetType.POI;
        }

        public RuleResult validate(VectorData data) {
            int total = data.features().size();
            if (total == 0) {
                return RuleResult.pass(code(), "피처가 없어 검사를 생략합니다.");
            }
            long invalid = data.features().stream()
                    .map(VectorFeature::geometry)
                    .filter(g -> g == null || g.isEmpty() || !g.isValid())
                    .count();
            double ratio = (double) invalid / total;
            if (ratio > ERROR_RATIO) {
                return RuleResult.error(code(), "유효하지 않은 geometry 비율이 높습니다: %d건 (%s)".formatted(invalid, percent(ratio)));
            }
            if (invalid > 0) {
                return RuleResult.warn(code(), "유효하지 않은 geometry %d건은 적재에서 제외됩니다.".formatted(invalid));
            }
            return RuleResult.pass(code(), "모든 geometry 가 유효합니다.");
        }
    }

    @Component
    public static class BoundsRule implements ValidationRule<VectorData> {
        public String code() {
            return BoundsCheck.CODE;
        }

        public boolean supports(DatasetType type) {
            return type == DatasetType.ROAD_NETWORK || type == DatasetType.POI;
        }

        public RuleResult validate(VectorData data) {
            return BoundsCheck.check(data.footprint());
        }
    }

    // 도로망 연결성: 끊어진 도로(고립된 연결 요소)가 많으면 경로탐색 품질이 떨어진다
    @Component
    public static class RoadConnectivityRule implements ValidationRule<VectorData> {
        static final double WARN_RATIO = 0.95;

        public String code() {
            return "ROAD_CONNECTIVITY";
        }

        public boolean supports(DatasetType type) {
            return type == DatasetType.ROAD_NETWORK;
        }

        public RuleResult validate(VectorData data) {
            RoadNetwork network = RoadNetworkBuilder.build(data.features());
            if (network.links().isEmpty()) {
                return RuleResult.error(code(), "도로 링크를 구성할 수 없습니다.");
            }
            RoadNetwork.Connectivity c = network.connectivity();
            String summary = "노드 %d개, 링크 %d개, 연결 요소 %d개, 최대 연결 요소 비율 %s"
                    .formatted(c.nodeCount(), network.links().size(), c.componentCount(), percent(c.largestComponentRatio()));
            if (c.largestComponentRatio() < WARN_RATIO) {
                return RuleResult.warn(code(), "끊어진 도로망이 있습니다. " + summary);
            }
            return RuleResult.pass(code(), summary);
        }
    }

    // 속도 정보가 없으면 도로 등급별 기본 속도를 쓰게 되므로 경고
    @Component
    public static class RoadAttributeRule implements ValidationRule<VectorData> {
        static final double WARN_RATIO = 0.5;

        public String code() {
            return "ROAD_ATTRIBUTES";
        }

        public boolean supports(DatasetType type) {
            return type == DatasetType.ROAD_NETWORK;
        }

        public RuleResult validate(VectorData data) {
            RoadNetwork network = RoadNetworkBuilder.build(data.features());
            if (network.links().isEmpty()) {
                return RuleResult.pass(code(), "링크가 없어 검사를 생략합니다.");
            }
            long noSpeed = network.links().stream().map(RoadLinkData::maxSpeedKph).filter(s -> s == null).count();
            long noClass = network.links().stream().map(RoadLinkData::roadClass).filter(s -> s == null).count();
            double noSpeedRatio = (double) noSpeed / network.links().size();
            String summary = "제한속도 누락 %s, 도로등급 누락 %s".formatted(
                    percent(noSpeedRatio), percent((double) noClass / network.links().size()));
            if (noSpeedRatio > WARN_RATIO || noClass > 0) {
                return RuleResult.warn(code(), summary + " (누락된 링크는 기본 속도 적용)");
            }
            return RuleResult.pass(code(), summary);
        }
    }

    @Component
    public static class PoiNameRule implements ValidationRule<VectorData> {
        static final double ERROR_RATIO = 0.5;

        public String code() {
            return "POI_NAME";
        }

        public boolean supports(DatasetType type) {
            return type == DatasetType.POI;
        }

        public RuleResult validate(VectorData data) {
            if (data.features().isEmpty()) {
                return RuleResult.pass(code(), "피처가 없어 검사를 생략합니다.");
            }
            long noName = data.features().stream().filter(f -> f.attr("name", "poi_name", "title") == null).count();
            double ratio = (double) noName / data.features().size();
            if (ratio > ERROR_RATIO) {
                return RuleResult.error(code(), "이름이 없는 POI 비율이 높습니다: " + percent(ratio));
            }
            if (noName > 0) {
                return RuleResult.warn(code(), "이름이 없는 POI %d건".formatted(noName));
            }
            return RuleResult.pass(code(), "모든 POI 에 이름이 있습니다.");
        }
    }
}
