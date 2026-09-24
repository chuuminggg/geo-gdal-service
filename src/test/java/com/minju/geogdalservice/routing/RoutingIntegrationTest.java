package com.minju.geogdalservice.routing;

import com.minju.geogdalservice.dto.RouteComparisonDto;
import com.minju.geogdalservice.dto.RouteDto;
import com.minju.geogdalservice.gis.GeometryUtils;
import com.minju.geogdalservice.support.PipelineTestSupport;
import com.minju.geogdalservice.support.TestGeoJson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 도로망 업로드 -> 검수 -> PostGIS 적재 -> 배포 -> 경로탐색 까지 E2E
 */
@EnabledIf("com.minju.geogdalservice.support.GdalTestSupport#gdalAvailable")
class RoutingIntegrationTest extends PipelineTestSupport {

    @Autowired
    RouteService routeService;

    private static final double STEP = 0.002;

    @Test
    void 배포된_도로망으로_경로를_탐색하고_Dijkstra와_A스타_결과가_같다() throws Exception {
        String name = uniqueName("gangnam");
        createDataset(name, "ROAD_NETWORK");
        // 강남 일대 10x10 격자 (교차로 간격 약 180~220m)
        uploadAndWait(name, "roads.geojson", bytes(TestGeoJson.gridRoads(127.02, 37.49, STEP, 10)), true);

        mockMvc.perform(get("/api/routes")
                        .param("fromLon", "127.02").param("fromLat", "37.49")
                        .param("toLon", String.valueOf(127.02 + 9 * STEP)).param("toLat", String.valueOf(37.49 + 9 * STEP))
                        .param("mode", "CAR").param("dataset", name))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.versionNo").value(1))
                .andExpect(jsonPath("$.data.geometry.type").value("LineString"));

        RouteDto route = routeService.route(name, 127.02, 37.49, 127.02 + 9 * STEP, 37.49 + 9 * STEP,
                TravelMode.CAR, ShortestPath.Algorithm.ASTAR);
        // 격자에서 최단 경로는 가로 9칸 + 세로 9칸 (맨해튼 거리)
        double expected = GeometryUtils.haversine(127.02, 37.49, 127.02 + 9 * STEP, 37.49)
                + GeometryUtils.haversine(127.02, 37.49, 127.02, 37.49 + 9 * STEP);
        assertThat(route.getDistanceM()).isCloseTo(expected, within(1.0));

        RouteComparisonDto comparison = routeService.compare(name, 127.02, 37.49, 127.02 + 9 * STEP, 37.49 + 9 * STEP,
                TravelMode.CAR);
        assertThat(comparison.isSameCost()).isTrue();
        assertThat(comparison.getAstar().getSettledNodes()).isLessThanOrEqualTo(comparison.getDijkstra().getSettledNodes());
    }

    @Test
    void 새_버전을_배포하면_경로탐색도_새_도로망을_사용하고_롤백하면_되돌아간다() throws Exception {
        String name = uniqueName("detour");
        createDataset(name, "ROAD_NETWORK");
        // v1: A - B 직선 도로 + 우회로 / v2: 직선 도로 폐쇄(우회로만)
        String direct = TestGeoJson.line(1, 127.000, 37.5, 127.004, 37.5, "primary", 50);
        List<String> detour = List.of(
                TestGeoJson.line(2, 127.000, 37.5, 127.000, 37.502, "primary", 50),
                TestGeoJson.line(3, 127.000, 37.502, 127.004, 37.502, "primary", 50),
                TestGeoJson.line(4, 127.004, 37.502, 127.004, 37.5, "primary", 50));
        List<String> v1 = new java.util.ArrayList<>(detour);
        v1.add(direct);
        uploadAndWait(name, "v1.geojson", bytes(TestGeoJson.collection(v1)), true);
        uploadAndWait(name, "v2.geojson", bytes(TestGeoJson.collection(detour)), false);

        double directM = GeometryUtils.haversine(127.000, 37.5, 127.004, 37.5);
        assertThat(route(name).getDistanceM()).isCloseTo(directM, within(0.5));

        mockMvc.perform(post("/api/datasets/{name}/versions/2/publish", name)).andExpect(status().isOk());
        RouteDto afterClosure = route(name);
        assertThat(afterClosure.getVersionNo()).isEqualTo(2);
        assertThat(afterClosure.getDistanceM()).isGreaterThan(directM * 2);

        mockMvc.perform(post("/api/datasets/{name}/rollback", name)).andExpect(status().isOk());
        assertThat(route(name).getVersionNo()).isEqualTo(1);
        assertThat(route(name).getDistanceM()).isCloseTo(directM, within(0.5));
    }

    @Test
    void 잘못된_요청_처리() throws Exception {
        String name = uniqueName("small");
        createDataset(name, "ROAD_NETWORK");
        uploadAndWait(name, "roads.geojson", bytes(TestGeoJson.gridRoads(127.02, 37.49, STEP, 3)), true);

        // 도로에서 먼 좌표
        mockMvc.perform(get("/api/routes").param("fromLon", "126.5").param("fromLat", "33.4")
                        .param("toLon", "127.02").param("toLat", "37.49").param("dataset", name))
                .andExpect(status().isBadRequest());
        // 잘못된 이동수단
        mockMvc.perform(get("/api/routes").param("fromLon", "127.02").param("fromLat", "37.49")
                        .param("toLon", "127.02").param("toLat", "37.49").param("mode", "PLANE").param("dataset", name))
                .andExpect(status().isBadRequest());
        // 배포 전 데이터셋
        String empty = uniqueName("empty");
        createDataset(empty, "ROAD_NETWORK");
        mockMvc.perform(get("/api/routes").param("fromLon", "127.02").param("fromLat", "37.49")
                        .param("toLon", "127.02").param("toLat", "37.49").param("dataset", empty))
                .andExpect(status().isNotFound());
    }

    private RouteDto route(String name) {
        return routeService.route(name, 127.000, 37.5, 127.004, 37.5, TravelMode.CAR, ShortestPath.Algorithm.ASTAR);
    }

    private byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
