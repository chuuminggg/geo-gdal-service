package com.minju.geogdalservice.service;

import com.jayway.jsonpath.JsonPath;
import com.minju.geogdalservice.dto.ElevationProfileDto;
import com.minju.geogdalservice.dto.PixelValueDto;
import com.minju.geogdalservice.dto.PoiDto;
import com.minju.geogdalservice.entity.VersionStatus;
import com.minju.geogdalservice.gis.GeometryUtils;
import com.minju.geogdalservice.support.GdalTestSupport;
import com.minju.geogdalservice.support.PipelineTestSupport;
import com.minju.geogdalservice.support.TestGeoJson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 배포된 데이터 조회 API: 픽셀 값(/vsis3/ COG), 고도, 좌표 변환, POI 반경/KNN 검색
 */
@EnabledIf("com.minju.geogdalservice.support.GdalTestSupport#gdalAvailable")
class SpatialQueryIntegrationTest extends PipelineTestSupport {

    @Autowired
    RasterQueryService rasterQueryService;
    @Autowired
    ElevationService elevationService;
    @Autowired
    CoordinateService coordinateService;
    @Autowired
    PoiService poiService;

    @TempDir
    Path tempDir;

    @Test
    void 좌표계가_다른_DEM도_경위도로_픽셀값을_조회한다() throws Exception {
        // EPSG:5186, 원점 (200000, 550000), 30m, 값 = col * 1000 + row
        String name = uniqueName("dem5186");
        createDataset(name, "RASTER");
        Path tif = GdalTestSupport.createGeoTiff(tempDir.resolve("dem.tif"), 5186, 200_000, 550_000, 30,
                100, 100, (col, row) -> col * 1000 + row, null);
        assertThat(uploadAndWait(name, "dem.tif", Files.readAllBytes(tif), true).getVersionStatus())
                .isEqualTo(VersionStatus.PUBLISHED);

        // 픽셀 (col=37, row=12) 중심의 5186 좌표를 경위도로 변환해서 조회
        double x = 200_000 + 37 * 30 + 15;
        double y = 550_000 - 12 * 30 - 15;
        double[] lonLat = coordinateService.transform(List.<double[]>of(new double[]{x, y}), 5186, 4326).get(0);

        PixelValueDto value = rasterQueryService.pixelValues(name, lonLat[0], lonLat[1]);

        assertThat(value.isInside()).isTrue();
        assertThat(value.getPixelX()).isEqualTo(37);
        assertThat(value.getPixelY()).isEqualTo(12);
        assertThat(value.getValues()).containsExactly(37_012.0);

        // 범위 밖
        assertThat(rasterQueryService.pixelValues(name, 129.0, 35.1).isInside()).isFalse();
    }

    @Test
    void DEM_고도_보간과_경로_고도_프로파일() throws Exception {
        // EPSG:4326, 원점 (127.0, 37.6), 0.0001도(약 9~11m), 고도 = col (동쪽으로 갈수록 1픽셀당 1m 상승)
        String name = uniqueName("dem4326");
        createDataset(name, "RASTER");
        Path tif = GdalTestSupport.createGeoTiff(tempDir.resolve("dem.tif"), 4326, 127.0, 37.6, 0.0001,
                300, 300, (col, row) -> col, -9999.0);
        uploadAndWait(name, "dem.tif", Files.readAllBytes(tif), true);

        // 픽셀 중심에서는 정확히 col 값, 두 픽셀 중심 사이에서는 선형 보간
        assertThat(elevationService.elevation(name, 127.0 + 0.0001 * 100.5, 37.59).getElevation())
                .isCloseTo(100.0, within(1e-6));
        assertThat(elevationService.elevation(name, 127.0 + 0.0001 * 101.0, 37.59).getElevation())
                .isCloseTo(100.5, within(1e-6));
        assertThat(elevationService.elevation(name, 128.0, 37.59).getElevation()).isNull();

        // 서 -> 동 (col 10.5 -> 210.5) 오르막 200m, 동 -> 서 복귀 내리막 200m
        ElevationProfileDto profile = elevationService.profile(name, List.of(
                new double[]{127.0 + 0.0001 * 10.5, 37.59},
                new double[]{127.0 + 0.0001 * 210.5, 37.59},
                new double[]{127.0 + 0.0001 * 10.5, 37.59}), 20.0);

        assertThat(profile.getAscentM()).isCloseTo(200, within(1e-6));
        assertThat(profile.getDescentM()).isCloseTo(200, within(1e-6));
        assertThat(profile.getMinElevation()).isCloseTo(10, within(1e-6));
        assertThat(profile.getMaxElevation()).isCloseTo(210, within(1e-6));
        assertThat(profile.getLengthM()).isCloseTo(
                2 * GeometryUtils.haversine(127.00105, 37.59, 127.02105, 37.59), within(1e-6));

        mockMvc.perform(get("/api/elevation").param("lon", "127.01005").param("lat", "37.59").param("dataset", name))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.elevation", closeTo(100.0, 1e-6)));
    }

    @Test
    void 배포되지_않은_래스터는_404() throws Exception {
        String name = uniqueName("empty");
        createDataset(name, "RASTER");
        mockMvc.perform(get("/api/rasters/{name}/value", name).param("lon", "127").param("lat", "37.5"))
                .andExpect(status().isNotFound());
    }

    @Test
    void 좌표계_변환() throws Exception {
        // EPSG:5186(중부원점)의 원점은 경위도 (127, 38) = (200000, 600000)
        double[] tm = coordinateService.transform(List.<double[]>of(new double[]{127.0, 38.0}), 4326, 5186).get(0);
        assertThat(tm[0]).isCloseTo(200_000, within(0.01));
        assertThat(tm[1]).isCloseTo(600_000, within(0.01));

        // UTM-K(5179) 왕복 변환
        String body = mockMvc.perform(post("/api/coordinates/transform")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"from\":4326,\"to\":5179,\"coordinates\":[[126.9780,37.5665]]}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        double x = ((Number) JsonPath.read(body, "$.data.coordinates[0][0]")).doubleValue();
        double y = ((Number) JsonPath.read(body, "$.data.coordinates[0][1]")).doubleValue();
        assertThat(x).isBetween(950_000.0, 960_000.0);
        assertThat(y).isBetween(1_945_000.0, 1_960_000.0);

        double[] back = coordinateService.transform(List.<double[]>of(new double[]{x, y}), 5179, 4326).get(0);
        assertThat(back[0]).isCloseTo(126.9780, within(1e-8));
        assertThat(back[1]).isCloseTo(37.5665, within(1e-8));

        mockMvc.perform(get("/api/coordinates/transform").param("x", "1").param("y", "1")
                        .param("from", "4326").param("to", "999999"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 배포된_POI를_반경_검색과_KNN으로_조회한다() throws Exception {
        String name = uniqueName("subway");
        createDataset(name, "POI");
        String pois = TestGeoJson.collection(List.of(
                TestGeoJson.point("강남역", "subway", 127.0276, 37.4979),
                TestGeoJson.point("역삼역", "subway", 127.0364, 37.5006),
                TestGeoJson.point("선릉역", "subway", 127.0490, 37.5045),
                TestGeoJson.point("강남역 카페", "cafe", 127.0280, 37.4985),
                TestGeoJson.point("서울역", "subway", 126.9707, 37.5547)));
        uploadAndWait(name, "pois.geojson", pois.getBytes(StandardCharsets.UTF_8), true);

        // 강남역에서 1.2km 이내 지하철역: 강남역(0m), 역삼역(약 830m). 선릉역(약 2km)·서울역 제외
        List<PoiDto> nearby = poiService.nearby(127.0276, 37.4979, 1200, "subway", name, 50);
        assertThat(nearby).extracting(PoiDto::getName).containsExactly("강남역", "역삼역");
        assertThat(nearby.get(1).getDistanceM())
                .isCloseTo(GeometryUtils.haversine(127.0276, 37.4979, 127.0364, 37.5006), within(5.0));

        // 서울역 근처에서 가장 가까운 2개 (카테고리 무관)
        List<PoiDto> nearest = poiService.nearest(126.97, 37.55, 2, null, name);
        assertThat(nearest).extracting(PoiDto::getName).containsExactly("서울역", "강남역 카페");

        mockMvc.perform(get("/api/pois/nearby").param("lon", "127.0276").param("lat", "37.4979")
                        .param("radius", "100").param("dataset", name))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
        mockMvc.perform(get("/api/pois/nearby").param("lon", "127").param("lat", "37.5").param("radius", "50000"))
                .andExpect(status().isBadRequest());
    }
}
