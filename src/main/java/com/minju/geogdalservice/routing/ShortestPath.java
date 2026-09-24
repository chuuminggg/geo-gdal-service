package com.minju.geogdalservice.routing;

import com.minju.geogdalservice.gis.GeometryUtils;

import java.util.Arrays;

/**
 * 최단(최소 시간) 경로 탐색: Dijkstra / A*
 *
 * A* 는 f(v) = g(v) + h(v) 순으로 노드를 확정한다.
 * h(v) = 목적지까지 직선거리 / 최대 속도 는
 *  - 과대평가하지 않고(admissible): 실제 경로는 직선보다 길고 최대 속도보다 느리다
 *  - 일관적이다(consistent): 간선 비용 >= 링크 길이 / 최대 속도 >= 끝점 간 직선거리 / 최대 속도
 * 따라서 한 번 확정된 노드는 다시 열 필요가 없고, 결과는 Dijkstra 와 같은 최적해다.
 * Dijkstra 는 h = 0 인 A* 와 같다.
 */
public final class ShortestPath {

    public enum Algorithm {DIJKSTRA, ASTAR}

    /**
     * @param edges        경로를 이루는 간선 (출발 -> 도착 순)
     * @param cost         총 비용 (초)
     * @param settledNodes 확정(방문)한 노드 수 - 탐색 효율 지표
     */
    public record Result(int[] edges, double cost, int settledNodes) {
    }

    private ShortestPath() {
    }

    /**
     * @return 경로가 없으면 null
     */
    public static Result find(RoadGraph g, int source, int target, TravelMode mode, Algorithm algorithm) {
        int n = g.nodeCount();
        double[] dist = new double[n];
        Arrays.fill(dist, Double.POSITIVE_INFINITY);
        int[] prevEdge = new int[n];
        Arrays.fill(prevEdge, -1);
        boolean[] settled = new boolean[n];

        // 초당 이동 가능한 최대 거리의 역수: h(v) = 직선거리 * secondsPerMeter
        double secondsPerMeter = algorithm == Algorithm.ASTAR ? 1.0 / mode.maxSpeedMps(g) : 0;
        double targetLon = g.lon(target);
        double targetLat = g.lat(target);

        MinHeap heap = new MinHeap(256);
        dist[source] = 0;
        heap.push(heuristic(g, source, targetLon, targetLat, secondsPerMeter), source);
        int settledCount = 0;

        while (!heap.isEmpty()) {
            int u = heap.pop();
            if (settled[u]) {
                continue;   // 이미 더 짧은 거리로 확정된 노드의 오래된 힙 항목 (lazy deletion)
            }
            settled[u] = true;
            settledCount++;
            if (u == target) {
                break;
            }
            for (int e = g.edgeStart(u), end = g.edgeEnd(u); e < end; e++) {
                int v = g.edgeTarget(e);
                if (settled[v]) {
                    continue;
                }
                double c = mode.cost(g, e);
                if (c == Double.POSITIVE_INFINITY) {
                    continue;
                }
                double nd = dist[u] + c;
                if (nd < dist[v]) {
                    dist[v] = nd;
                    prevEdge[v] = e;
                    heap.push(nd + heuristic(g, v, targetLon, targetLat, secondsPerMeter), v);
                }
            }
        }

        if (dist[target] == Double.POSITIVE_INFINITY) {
            return null;
        }
        return new Result(reconstruct(g, prevEdge, source, target), dist[target], settledCount);
    }

    private static double heuristic(RoadGraph g, int v, double targetLon, double targetLat, double secondsPerMeter) {
        if (secondsPerMeter == 0) {
            return 0;
        }
        return GeometryUtils.haversine(g.lon(v), g.lat(v), targetLon, targetLat) * secondsPerMeter;
    }

    private static int[] reconstruct(RoadGraph g, int[] prevEdge, int source, int target) {
        int count = 0;
        for (int v = target; v != source; v = g.edgeSource(prevEdge[v])) {
            count++;
        }
        int[] edges = new int[count];
        int i = count;
        for (int v = target; v != source; v = g.edgeSource(prevEdge[v])) {
            edges[--i] = prevEdge[v];
        }
        return edges;
    }
}
