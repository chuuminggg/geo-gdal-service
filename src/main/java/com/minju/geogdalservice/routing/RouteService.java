package com.minju.geogdalservice.routing;

import com.minju.geogdalservice.common.exception.NotFoundException;
import com.minju.geogdalservice.dto.RouteComparisonDto;
import com.minju.geogdalservice.dto.RouteDto;
import com.minju.geogdalservice.gis.GeoJsonMapper;
import com.minju.geogdalservice.gis.GeometryUtils;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Coordinate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class RouteService {

    private final RoadGraphProvider graphProvider;

    @Value("${geo.routing.default-dataset:}")
    private String defaultDataset;

    // 요청 좌표에서 이 거리 안에 도로망 노드가 없으면 경로탐색 불가
    @Value("${geo.routing.max-snap-distance-m:500}")
    private double maxSnapDistanceM;

    public RouteDto route(String dataset, double fromLon, double fromLat, double toLon, double toLat,
                          TravelMode mode, ShortestPath.Algorithm algorithm) {
        RoadGraph graph = graphProvider.get(resolveDataset(dataset));
        return route(graph, fromLon, fromLat, toLon, toLat, mode, algorithm);
    }

    public RouteComparisonDto compare(String dataset, double fromLon, double fromLat, double toLon, double toLat,
                                      TravelMode mode) {
        RoadGraph graph = graphProvider.get(resolveDataset(dataset));
        RouteDto dijkstra = route(graph, fromLon, fromLat, toLon, toLat, mode, ShortestPath.Algorithm.DIJKSTRA);
        RouteDto astar = route(graph, fromLon, fromLat, toLon, toLat, mode, ShortestPath.Algorithm.ASTAR);
        boolean sameCost = Math.abs(dijkstra.getDurationS() - astar.getDurationS()) < 1e-6;
        return new RouteComparisonDto(dijkstra, astar, sameCost,
                (double) astar.getSettledNodes() / Math.max(1, dijkstra.getSettledNodes()));
    }

    RouteDto route(RoadGraph graph, double fromLon, double fromLat, double toLon, double toLat,
                   TravelMode mode, ShortestPath.Algorithm algorithm) {
        GeometryUtils.validateLonLat(fromLon, fromLat);
        GeometryUtils.validateLonLat(toLon, toLat);

        int source = snap(graph, fromLon, fromLat, "출발지");
        int target = snap(graph, toLon, toLat, "도착지");

        long start = System.nanoTime();
        ShortestPath.Result result = ShortestPath.find(graph, source, target, mode, algorithm);
        double elapsedMs = (System.nanoTime() - start) / 1e6;

        if (result == null) {
            throw new NotFoundException("%s 로 이동 가능한 경로가 없습니다.".formatted(mode));
        }
        return toDto(graph, source, result, mode, algorithm, elapsedMs,
                GeometryUtils.haversine(fromLon, fromLat, graph.lon(source), graph.lat(source)),
                GeometryUtils.haversine(toLon, toLat, graph.lon(target), graph.lat(target)));
    }

    private int snap(RoadGraph graph, double lon, double lat, String label) {
        int node = graph.nearestNode(lon, lat, maxSnapDistanceM);
        if (node < 0) {
            throw new IllegalArgumentException("%s 반경 %.0fm 안에 도로가 없습니다.".formatted(label, maxSnapDistanceM));
        }
        return node;
    }

    private RouteDto toDto(RoadGraph g, int source, ShortestPath.Result result, TravelMode mode, ShortestPath.Algorithm algorithm,
                           double elapsedMs, double snapFrom, double snapTo) {
        double distance = 0;
        double ascent = 0;
        double descent = 0;
        List<Coordinate> coordinates = new ArrayList<>();
        List<RouteDto.RoadSegment> roads = new ArrayList<>();

        for (int e : result.edges()) {
            int link = g.edgeLink(e);
            double length = g.edgeLength(e);
            distance += length;

            double rise = g.elevation(g.edgeTarget(e)) - g.elevation(g.edgeSource(e));
            if (!Double.isNaN(rise)) {
                if (rise > 0) ascent += rise;
                else descent -= rise;
            }

            appendLinkCoordinates(coordinates, g.linkCoords(link), g.edgeForward(e));

            // 같은 이름·등급의 도로를 연속으로 지나면 한 구간으로 합친다
            RouteDto.RoadSegment last = roads.isEmpty() ? null : roads.get(roads.size() - 1);
            if (last != null && Objects.equals(last.getName(), g.linkName(link))
                    && Objects.equals(last.getRoadClass(), g.linkRoadClass(link))) {
                last.setDistanceM(last.getDistanceM() + length);
            } else {
                roads.add(new RouteDto.RoadSegment(g.linkName(link), g.linkRoadClass(link), length));
            }
        }
        if (coordinates.isEmpty()) {
            // 출발지와 도착지가 같은 노드로 스냅된 경우: 길이 0 인 라인
            Coordinate c = new Coordinate(g.lon(source), g.lat(source));
            coordinates.add(c);
            coordinates.add(new Coordinate(c));
        }

        return RouteDto.builder()
                .datasetName(g.datasetName())
                .versionNo(g.versionNo())
                .mode(mode)
                .algorithm(algorithm)
                .distanceM(distance)
                .durationS(result.cost())
                .ascentM(g.hasElevation() ? ascent : null)
                .descentM(g.hasElevation() ? descent : null)
                .snapFromM(snapFrom)
                .snapToM(snapTo)
                .settledNodes(result.settledNodes())
                .elapsedMs(elapsedMs)
                .geometry(GeoJsonMapper.toGeoJson(GeometryUtils.FACTORY.createLineString(
                        coordinates.toArray(Coordinate[]::new))))
                .roads(roads)
                .build();
    }

    private void appendLinkCoordinates(List<Coordinate> out, double[] flat, boolean forward) {
        int points = flat.length / 2;
        for (int i = 0; i < points; i++) {
            int idx = forward ? i : points - 1 - i;
            // 앞 링크의 끝점과 이번 링크의 시작점은 같은 노드이므로 중복 제거
            if (i == 0 && !out.isEmpty()) {
                continue;
            }
            out.add(new Coordinate(flat[idx * 2], flat[idx * 2 + 1]));
        }
    }

    private String resolveDataset(String dataset) {
        if (dataset != null && !dataset.isBlank()) {
            return dataset;
        }
        if (defaultDataset == null || defaultDataset.isBlank()) {
            throw new IllegalArgumentException("도로 네트워크 데이터셋을 지정해주세요. (dataset 파라미터 또는 geo.routing.default-dataset)");
        }
        return defaultDataset;
    }
}
