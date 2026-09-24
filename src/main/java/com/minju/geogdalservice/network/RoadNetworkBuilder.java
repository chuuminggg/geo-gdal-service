package com.minju.geogdalservice.network;

import com.minju.geogdalservice.gis.GeometryUtils;
import com.minju.geogdalservice.gis.VectorFeature;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 도로 라인 피처로부터 노드-링크 토폴로지를 구성한다.
 *
 * 링크의 시작점/끝점 좌표를 격자(약 1cm)에 스냅해서 같은 좌표를 공유하는 끝점을 하나의 노드로 합친다.
 * 교차로에서 링크가 분리되어 있는(noded) 데이터를 전제로 한다. (표준노드링크, OSM 전처리 데이터 등)
 */
public final class RoadNetworkBuilder {

    // 1e-7도 ≈ 1.1cm
    private static final double SNAP_SCALE = 1e7;

    private RoadNetworkBuilder() {
    }

    public static RoadNetwork build(List<VectorFeature> features) {
        Map<NodeKey, Integer> nodeIndex = new HashMap<>();
        List<Coordinate> nodes = new ArrayList<>();
        List<RoadLinkData> links = new ArrayList<>();
        int skipped = 0;

        for (VectorFeature feature : features) {
            List<LineString> lines = lines(feature.geometry());
            if (lines.isEmpty()) {
                skipped++;
                continue;
            }
            Direction direction = direction(feature.attr("oneway", "one_way"));
            for (LineString line : lines) {
                if (line.isEmpty() || line.getNumPoints() < 2 || !line.isValid()) {
                    skipped++;
                    continue;
                }
                LineString oriented = direction == Direction.REVERSE ? line.reverse() : line;
                double length = GeometryUtils.lengthMeters(oriented);
                if (length <= 0) {
                    skipped++;
                    continue;
                }
                int from = node(oriented.getCoordinateN(0), nodeIndex, nodes);
                int to = node(oriented.getCoordinateN(oriented.getNumPoints() - 1), nodeIndex, nodes);

                links.add(new RoadLinkData(
                        feature.attr("link_id", "id", "osm_id"),
                        from, to,
                        feature.attr("road_class", "highway", "road_rank", "class"),
                        feature.attr("name", "road_name"),
                        direction != Direction.BOTH,
                        speedKph(feature.attr("max_speed", "maxspeed", "max_spd", "speed")),
                        length,
                        oriented));
            }
        }
        return new RoadNetwork(nodes, links, skipped);
    }

    private static int node(Coordinate c, Map<NodeKey, Integer> nodeIndex, List<Coordinate> nodes) {
        NodeKey key = new NodeKey(Math.round(c.x * SNAP_SCALE), Math.round(c.y * SNAP_SCALE));
        return nodeIndex.computeIfAbsent(key, k -> {
            nodes.add(new Coordinate(c.x, c.y));
            return nodes.size() - 1;
        });
    }

    private static List<LineString> lines(Geometry geometry) {
        List<LineString> result = new ArrayList<>();
        if (geometry instanceof LineString line) {
            result.add(line);
        } else if (geometry instanceof MultiLineString multi) {
            for (int i = 0; i < multi.getNumGeometries(); i++) {
                result.add((LineString) multi.getGeometryN(i));
            }
        }
        return result;
    }

    enum Direction {BOTH, FORWARD, REVERSE}

    // OSM 관례: yes/true/1 = 정방향 일방통행, -1/reverse = 역방향 일방통행
    static Direction direction(String oneway) {
        if (oneway == null) {
            return Direction.BOTH;
        }
        return switch (oneway.toLowerCase(Locale.ROOT)) {
            case "yes", "true", "1", "y", "t", "forward" -> Direction.FORWARD;
            case "-1", "reverse", "backward" -> Direction.REVERSE;
            default -> Direction.BOTH;
        };
    }

    // "50", "50 km/h", "30 mph" 형태의 제한속도 파싱
    static Integer speedKph(String value) {
        if (value == null) {
            return null;
        }
        String digits = value.replaceAll("^\\D*(\\d+(?:\\.\\d+)?).*$", "$1");
        try {
            double speed = Double.parseDouble(digits);
            if (value.toLowerCase(Locale.ROOT).contains("mph")) {
                speed *= 1.609344;
            }
            return speed > 0 ? (int) Math.round(speed) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private record NodeKey(long x, long y) {
    }
}
