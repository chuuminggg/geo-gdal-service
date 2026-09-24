package com.minju.geogdalservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 같은 출발/도착지에 대한 Dijkstra vs A* 비교
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RouteComparisonDto {
    private RouteDto dijkstra;
    private RouteDto astar;
    // 두 알고리즘의 최소 비용이 같은지 (A* 최적성 검증)
    private boolean sameCost;
    // A* 가 확정한 노드 수 / Dijkstra 가 확정한 노드 수
    private double settledRatio;
}
