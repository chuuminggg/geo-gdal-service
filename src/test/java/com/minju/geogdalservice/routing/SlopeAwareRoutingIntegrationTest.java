package com.minju.geogdalservice.routing;

import com.minju.geogdalservice.dto.RouteDto;
import com.minju.geogdalservice.gis.GeometryUtils;
import com.minju.geogdalservice.support.GdalTestSupport;
import com.minju.geogdalservice.support.PipelineTestSupport;
import com.minju.geogdalservice.support.TestGeoJson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 배포된 DEM 의 고도를 도로 그래프 노드에 반영해 보행 경로가 언덕을 피하는지 검증
 */
@EnabledIf("com.minju.geogdalservice.support.GdalTestSupport#gdalAvailable")
@TestPropertySource(properties = "geo.routing.elevation-dataset=" + SlopeAwareRoutingIntegrationTest.DEM)
class SlopeAwareRoutingIntegrationTest extends PipelineTestSupport {

    static final String DEM = "hill-dem";

    @Autowired
    RouteService routeService;

    @TempDir
    Path tempDir;

    @Test
    void DEM이_배포되면_보행_경로는_언덕을_우회한다() throws Exception {
        // 직선 A(127.000) - H(127.002) - B(127.004) 와 북쪽 평지 우회로
        String roads = uniqueName("hill-roads");
        createDataset(roads, "ROAD_NETWORK");
        uploadAndWait(roads, "roads.geojson", TestGeoJson.collection(List.of(
                TestGeoJson.line(1, 127.000, 37.5, 127.002, 37.5, "residential", 30),
                TestGeoJson.line(2, 127.002, 37.5, 127.004, 37.5, "residential", 30),
                TestGeoJson.line(3, 127.000, 37.5, 127.000, 37.502, "residential", 30),
                TestGeoJson.line(4, 127.000, 37.502, 127.004, 37.502, "residential", 30),
                TestGeoJson.line(5, 127.004, 37.502, 127.004, 37.5, "residential", 30)))
                .getBytes(StandardCharsets.UTF_8), true);

        double directM = GeometryUtils.haversine(127.000, 37.5, 127.004, 37.5);

        // DEM 배포 전: 고도 정보 없음 -> 직선
        RouteDto before = walk(roads);
        assertThat(before.getDistanceM()).isCloseTo(directM, within(0.5));
        assertThat(before.getAscentM()).isNull();

        // DEM: 원점 (126.999, 37.503), 0.0001도, H(127.002, 37.5) 주변만 높이 60m
        createDataset(DEM, "RASTER");
        Path tif = GdalTestSupport.createGeoTiff(tempDir.resolve("hill.tif"), 4326, 126.999, 37.503, 0.0001,
                60, 50, (col, row) -> (Math.abs(col - 30) <= 2 && row >= 25) ? 60 : 0, null);
        uploadAndWait(DEM, "hill.tif", Files.readAllBytes(tif), true);

        // DEM 배포 이벤트로 그래프가 다시 로드되면 보행자는 평지 우회로 선택
        RouteDto after = walk(roads);
        assertThat(after.getDistanceM()).isGreaterThan(directM * 2);
        assertThat(after.getAscentM()).isCloseTo(0.0, within(1e-9));

        // 차량은 경사와 무관하게 직선
        RouteDto car = routeService.route(roads, 127.000, 37.5, 127.004, 37.5, TravelMode.CAR, ShortestPath.Algorithm.ASTAR);
        assertThat(car.getDistanceM()).isCloseTo(directM, within(0.5));
        assertThat(car.getAscentM()).isCloseTo(60.0, within(1e-6));
    }

    private RouteDto walk(String roads) {
        return routeService.route(roads, 127.000, 37.5, 127.004, 37.5, TravelMode.WALK, ShortestPath.Algorithm.ASTAR);
    }
}
