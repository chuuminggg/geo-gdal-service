package com.minju.geogdalservice.routing;

import com.minju.geogdalservice.gis.VectorFeature;
import com.minju.geogdalservice.network.RoadNetwork;
import com.minju.geogdalservice.network.RoadNetworkBuilder;
import com.minju.geogdalservice.routing.ShortestPath.Algorithm;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static com.minju.geogdalservice.support.TestFeatures.line;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ShortestPathTest {

    @Test
    void Dijkstra_결과는_Bellman_Ford와_같다() {
        RoadGraph g = randomGrid(8, new Random(11));
        int n = g.nodeCount();
        for (int source = 0; source < n; source += 7) {
            double[] expected = bellmanFord(g, source, TravelMode.CAR);
            for (int target = 0; target < n; target++) {
                ShortestPath.Result r = ShortestPath.find(g, source, target, TravelMode.CAR, Algorithm.DIJKSTRA);
                if (expected[target] == Double.POSITIVE_INFINITY) {
                    assertThat(r).isNull();
                } else {
                    assertThat(r.cost()).isCloseTo(expected[target], within(1e-6));
                }
            }
        }
    }

    @Test
    void A스타는_Dijkstra와_같은_최적해를_더_적은_노드_탐색으로_찾는다() {
        RoadGraph g = randomGrid(40, new Random(5));
        Random random = new Random(9);
        long dijkstraSettled = 0;
        long astarSettled = 0;

        for (int i = 0; i < 200; i++) {
            int s = random.nextInt(g.nodeCount());
            int t = random.nextInt(g.nodeCount());
            for (TravelMode mode : TravelMode.values()) {
                ShortestPath.Result d = ShortestPath.find(g, s, t, mode, Algorithm.DIJKSTRA);
                ShortestPath.Result a = ShortestPath.find(g, s, t, mode, Algorithm.ASTAR);
                assertThat(a == null).isEqualTo(d == null);
                if (d == null) {
                    continue;
                }
                assertThat(a.cost()).isCloseTo(d.cost(), within(1e-6));
                assertThat(pathCost(g, a.edges(), mode)).isCloseTo(a.cost(), within(1e-6));
                dijkstraSettled += d.settledNodes();
                astarSettled += a.settledNodes();
            }
        }
        System.out.printf("[ShortestPath] 40x40 grid, 600 queries: settled nodes dijkstra=%d, A*=%d (%.1f%%)%n",
                dijkstraSettled, astarSettled, 100.0 * astarSettled / dijkstraSettled);
        assertThat(astarSettled).isLessThan(dijkstraSettled);
    }

    @Test
    void 경로의_간선은_출발지에서_도착지까지_이어진다() {
        RoadGraph g = randomGrid(10, new Random(1));
        ShortestPath.Result r = ShortestPath.find(g, 0, g.nodeCount() - 1, TravelMode.CAR, Algorithm.ASTAR);

        assertThat(g.edgeSource(r.edges()[0])).isEqualTo(0);
        assertThat(g.edgeTarget(r.edges()[r.edges().length - 1])).isEqualTo(g.nodeCount() - 1);
        for (int i = 1; i < r.edges().length; i++) {
            assertThat(g.edgeSource(r.edges()[i])).isEqualTo(g.edgeTarget(r.edges()[i - 1]));
        }
    }

    @Test
    void 일방통행은_차량만_제한하고_보행은_역방향_통행_가능() {
        // A -> B 직선은 B -> A 방향 일방통행, 우회로 A - C - D - B 는 양방향
        RoadGraph g = graph(List.of(
                line(Map.of("oneway", "-1", "highway", "primary"), 127.000, 37.5, 127.004, 37.5),
                line(Map.of("highway", "primary"), 127.000, 37.5, 127.000, 37.502),
                line(Map.of("highway", "primary"), 127.000, 37.502, 127.004, 37.502),
                line(Map.of("highway", "primary"), 127.004, 37.502, 127.004, 37.5)), null);
        int a = g.nearestNode(127.000, 37.5, 10);
        int b = g.nearestNode(127.004, 37.5, 10);

        assertThat(ShortestPath.find(g, a, b, TravelMode.CAR, Algorithm.ASTAR).edges()).hasSize(3);
        assertThat(ShortestPath.find(g, b, a, TravelMode.CAR, Algorithm.ASTAR).edges()).hasSize(1);
        assertThat(ShortestPath.find(g, a, b, TravelMode.WALK, Algorithm.ASTAR).edges()).hasSize(1);
    }

    @Test
    void 보행자는_자동차전용도로를_지나지_않는다() {
        RoadGraph g = graph(List.of(
                line(Map.of("highway", "motorway"), 127.000, 37.5, 127.004, 37.5),
                line(Map.of("highway", "residential"), 127.000, 37.5, 127.000, 37.502),
                line(Map.of("highway", "residential"), 127.000, 37.502, 127.004, 37.502),
                line(Map.of("highway", "residential"), 127.004, 37.502, 127.004, 37.5)), null);
        int a = g.nearestNode(127.000, 37.5, 10);
        int b = g.nearestNode(127.004, 37.5, 10);

        assertThat(ShortestPath.find(g, a, b, TravelMode.CAR, Algorithm.ASTAR).edges()).hasSize(1);
        assertThat(ShortestPath.find(g, a, b, TravelMode.WALK, Algorithm.ASTAR).edges()).hasSize(3);
        assertThat(ShortestPath.find(g, a, b, TravelMode.BIKE, Algorithm.ASTAR).edges()).hasSize(3);
    }

    @Test
    void 보행자는_가파른_언덕보다_평탄한_우회로를_선택한다() {
        // 직선 A - H(언덕 60m) - B 약 353m, 평지 우회 A - C - D - B 약 797m
        List<VectorFeature> features = List.of(
                line(127.000, 37.5, 127.002, 37.5),
                line(127.002, 37.5, 127.004, 37.5),
                line(127.000, 37.5, 127.000, 37.502),
                line(127.000, 37.502, 127.004, 37.502),
                line(127.004, 37.502, 127.004, 37.5));
        RoadNetwork network = RoadNetworkBuilder.build(features);
        double[] elevations = new double[network.nodes().size()];
        for (int i = 0; i < elevations.length; i++) {
            elevations[i] = network.nodes().get(i).x == 127.002 ? 60 : 0;
        }
        RoadGraph flat = graph(features, null);
        RoadGraph hilly = graph(features, elevations);
        int a = hilly.nearestNode(127.000, 37.5, 10);
        int b = hilly.nearestNode(127.004, 37.5, 10);

        assertThat(ShortestPath.find(flat, a, b, TravelMode.WALK, Algorithm.ASTAR).edges()).hasSize(2);
        assertThat(ShortestPath.find(hilly, a, b, TravelMode.WALK, Algorithm.ASTAR).edges()).hasSize(3);
        // 차량은 경사를 고려하지 않음
        assertThat(ShortestPath.find(hilly, a, b, TravelMode.CAR, Algorithm.ASTAR).edges()).hasSize(2);
    }

    @Test
    void 끊어진_도로망이면_경로가_없다() {
        RoadGraph g = graph(List.of(
                line(127.000, 37.5, 127.001, 37.5),
                line(127.100, 37.5, 127.101, 37.5)), null);
        int a = g.nearestNode(127.000, 37.5, 10);
        int b = g.nearestNode(127.101, 37.5, 10);

        assertThat(ShortestPath.find(g, a, b, TravelMode.CAR, Algorithm.ASTAR)).isNull();
        assertThat(ShortestPath.find(g, a, a, TravelMode.CAR, Algorithm.ASTAR).edges()).isEmpty();
    }

    @Test
    void 이동수단별_속도_모델() {
        // Tobler: -5% 내리막에서 최대 6km/h, 평지 약 5.04km/h
        assertThat(TravelMode.toblerKph(-0.05)).isEqualTo(6.0);
        assertThat(TravelMode.toblerKph(0)).isCloseTo(5.04, within(0.01));
        assertThat(TravelMode.toblerKph(0.2)).isLessThan(TravelMode.toblerKph(-0.2));
        // 자전거: 평지 15, 오르막 감속(하한 3), 내리막 가속(상한 30)
        assertThat(TravelMode.bikeKph(0)).isEqualTo(15.0);
        assertThat(TravelMode.bikeKph(0.05)).isCloseTo(9.0, within(1e-9));
        assertThat(TravelMode.bikeKph(0.5)).isEqualTo(3.0);
        assertThat(TravelMode.bikeKph(-0.5)).isEqualTo(30.0);
        // 도로 등급: OSM 태그 / 표준노드링크 코드
        assertThat(RoadRules.carSpeedKph("motorway", 0)).isEqualTo(100);
        assertThat(RoadRules.carSpeedKph("101", 0)).isEqualTo(100);
        assertThat(RoadRules.carSpeedKph("primary", 70)).isEqualTo(70);
        assertThat(RoadRules.walkAllowed("102")).isFalse();
        assertThat(RoadRules.carAllowed("footway")).isFalse();
        assertThat(RoadRules.bikeAllowed("steps")).isFalse();
    }

    // n x n 격자, 링크마다 무작위 등급/제한속도/일방통행, 일부 링크 누락
    static RoadGraph randomGrid(int n, Random random) {
        String[] classes = {"primary", "secondary", "residential", "footway", "motorway"};
        List<VectorFeature> features = new ArrayList<>();
        double step = 0.001;
        for (int row = 0; row < n; row++) {
            for (int col = 0; col < n; col++) {
                double lon = 127.0 + col * step;
                double lat = 37.5 + row * step;
                for (int dir = 0; dir < 2; dir++) {
                    if ((dir == 0 && col == n - 1) || (dir == 1 && row == n - 1) || random.nextInt(10) == 0) {
                        continue;
                    }
                    double lon2 = dir == 0 ? lon + step : lon;
                    double lat2 = dir == 1 ? lat + step : lat;
                    String oneway = random.nextInt(5) == 0 ? "yes" : "no";
                    features.add(line(Map.of(
                            "highway", classes[random.nextInt(classes.length)],
                            "maxspeed", String.valueOf(20 + random.nextInt(60)),
                            "oneway", oneway), lon, lat, lon2, lat2));
                }
            }
        }
        RoadNetwork network = RoadNetworkBuilder.build(features);
        double[] elevations = new double[network.nodes().size()];
        for (int i = 0; i < elevations.length; i++) {
            elevations[i] = random.nextDouble() * 30;
        }
        return RoadGraph.builder().nodes(network.nodes()).links(network.links()).elevations(elevations).build();
    }

    static RoadGraph graph(List<VectorFeature> features, double[] elevations) {
        RoadNetwork network = RoadNetworkBuilder.build(features);
        return RoadGraph.builder().version(1, "test", 1)
                .nodes(network.nodes()).links(network.links()).elevations(elevations).build();
    }

    private static double[] bellmanFord(RoadGraph g, int source, TravelMode mode) {
        double[] dist = new double[g.nodeCount()];
        Arrays.fill(dist, Double.POSITIVE_INFINITY);
        dist[source] = 0;
        for (int iter = 0; iter < g.nodeCount() - 1; iter++) {
            boolean changed = false;
            for (int e = 0; e < g.edgeCount(); e++) {
                double c = mode.cost(g, e);
                int u = g.edgeSource(e);
                int v = g.edgeTarget(e);
                if (dist[u] + c < dist[v]) {
                    dist[v] = dist[u] + c;
                    changed = true;
                }
            }
            if (!changed) {
                break;
            }
        }
        return dist;
    }

    private static double pathCost(RoadGraph g, int[] edges, TravelMode mode) {
        double sum = 0;
        for (int e : edges) {
            sum += mode.cost(g, e);
        }
        return sum;
    }
}
