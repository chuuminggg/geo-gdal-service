package com.minju.geogdalservice.dto;

import com.minju.geogdalservice.routing.ShortestPath;
import com.minju.geogdalservice.routing.TravelMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RouteDto {
    private String datasetName;
    private int versionNo;
    private TravelMode mode;
    private ShortestPath.Algorithm algorithm;

    private double distanceM;
    private double durationS;
    // DEM 이 있을 때만 의미 있음
    private Double ascentM;
    private Double descentM;

    // 요청 좌표에서 도로망 노드까지 스냅된 거리
    private double snapFromM;
    private double snapToM;

    // 탐색 효율
    private int settledNodes;
    private double elapsedMs;

    private Map<String, Object> geometry;   // GeoJSON LineString
    private List<RoadSegment> roads;         // 같은 도로를 연속으로 지나는 구간 요약

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoadSegment {
        private String name;
        private String roadClass;
        private double distanceM;
    }
}
