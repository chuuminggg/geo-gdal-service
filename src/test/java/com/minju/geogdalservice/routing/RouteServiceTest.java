package com.minju.geogdalservice.routing;

import com.minju.geogdalservice.dto.RouteDto;
import com.minju.geogdalservice.gis.GeometryUtils;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static com.minju.geogdalservice.support.TestFeatures.line;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class RouteServiceTest {

    private final RouteService routeService = new RouteService(null);

    {
        ReflectionTestUtils.setField(routeService, "maxSnapDistanceM", 200.0);
    }

    // 테헤란로(동서) 2개 링크 + 강남대로(남북) 1개 링크, 테헤란로 두 번째 링크는 역방향으로 저장됨
    private final RoadGraph graph = ShortestPathTest.graph(List.of(
            line(Map.of("name", "테헤란로", "highway", "primary"), 127.000, 37.5, 127.002, 37.5),
            line(Map.of("name", "테헤란로", "highway", "primary"), 127.004, 37.5, 127.002, 37.5),
            line(Map.of("name", "강남대로", "highway", "trunk"), 127.004, 37.5, 127.004, 37.503)), null);

    @Test
    void 경로_geometry는_링크_방향에_맞게_이어붙이고_같은_도로는_구간을_합친다() {
        RouteDto route = routeService.route(graph, 127.0001, 37.5001, 127.004, 37.503,
                TravelMode.CAR, ShortestPath.Algorithm.ASTAR);

        @SuppressWarnings("unchecked")
        List<double[]> coords = (List<double[]>) route.getGeometry().get("coordinates");
        assertThat(coords).extracting(c -> c[0]).containsExactly(127.000, 127.002, 127.004, 127.004);
        assertThat(coords).extracting(c -> c[1]).containsExactly(37.5, 37.5, 37.5, 37.503);

        assertThat(route.getRoads()).extracting(RouteDto.RoadSegment::getName).containsExactly("테헤란로", "강남대로");
        assertThat(route.getRoads().get(0).getDistanceM())
                .isCloseTo(GeometryUtils.haversine(127.0, 37.5, 127.004, 37.5), within(0.5));
        assertThat(route.getDistanceM()).isCloseTo(
                route.getRoads().get(0).getDistanceM() + route.getRoads().get(1).getDistanceM(), within(1e-9));
        assertThat(route.getSnapFromM()).isCloseTo(GeometryUtils.haversine(127.0001, 37.5001, 127.0, 37.5), within(1e-6));
        assertThat(route.getAscentM()).isNull();   // 고도 정보 없음
    }

    @Test
    void 도로에서_너무_먼_좌표는_거부한다() {
        assertThatThrownBy(() -> routeService.route(graph, 127.1, 37.6, 127.004, 37.503,
                TravelMode.CAR, ShortestPath.Algorithm.ASTAR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("출발지");
    }

    @Test
    void 출발지와_도착지가_같은_노드면_거리_0() {
        RouteDto route = routeService.route(graph, 127.0, 37.5, 127.0001, 37.5,
                TravelMode.WALK, ShortestPath.Algorithm.ASTAR);
        assertThat(route.getDistanceM()).isZero();
        assertThat(route.getRoads()).isEmpty();
    }
}
