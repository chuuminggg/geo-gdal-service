package com.minju.geogdalservice.pipeline;

import com.minju.geogdalservice.dto.DatasetVersionDto;
import com.minju.geogdalservice.dto.JobDto;
import com.minju.geogdalservice.dto.StatusHistoryDto;
import com.minju.geogdalservice.dto.ValidationResultDto;
import com.minju.geogdalservice.entity.JobStatus;
import com.minju.geogdalservice.entity.PipelineStage;
import com.minju.geogdalservice.entity.VersionStatus;
import com.minju.geogdalservice.repository.RoadNetworkRepository;
import com.minju.geogdalservice.service.DatasetService;
import com.minju.geogdalservice.service.S3Service;
import com.minju.geogdalservice.support.GdalTestSupport;
import com.minju.geogdalservice.support.PipelineTestSupport;
import com.minju.geogdalservice.support.TestGeoJson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@EnabledIf("com.minju.geogdalservice.support.GdalTestSupport#gdalAvailable")
class PipelineIntegrationTest extends PipelineTestSupport {

    @Autowired
    DatasetService datasetService;
    @Autowired
    S3Service s3Service;
    @Autowired
    RoadNetworkRepository roadNetworkRepository;
    @Autowired
    PipelineRecovery pipelineRecovery;
    @Autowired
    JdbcTemplate jdbcTemplate;

    @TempDir
    Path tempDir;

    @Test
    void 래스터_업로드부터_검수_COG변환_배포까지() throws Exception {
        String name = uniqueName("dem");
        createDataset(name, "RASTER");

        JobDto job = uploadAndWait(name, "dem.tif", seoulDem("dem.tif"), true);

        assertThat(job.getStatus()).isEqualTo(JobStatus.SUCCEEDED);
        DatasetVersionDto v1 = datasetService.findVersion(name, 1);
        assertThat(v1.getStatus()).isEqualTo(VersionStatus.PUBLISHED);
        assertThat(v1.isActive()).isTrue();
        assertThat(v1.getRaster().getEpsg()).isEqualTo(5186);
        assertThat(v1.getFootprint()).isNotNull();
        // COG 가 S3 processed/ 영역에 저장됨
        assertThat(v1.getProcessedKey()).startsWith("processed/" + name + "/v1/");
        assertThat(s3Service.exists(bucketName, v1.getProcessedKey())).isTrue();

        assertThat(queryService.getHistory(name, 1)).extracting(StatusHistoryDto::getToStatus).containsExactly(
                VersionStatus.UPLOADED, VersionStatus.VALIDATING, VersionStatus.VALIDATED,
                VersionStatus.PROCESSING, VersionStatus.PROCESSED, VersionStatus.PUBLISHED);

        // 배포된 데이터는 bbox 공간 검색에 나타난다
        assertThat(datasetService.searchByBbox(127.0, 37.5, 127.05, 37.6, null))
                .extracting(DatasetVersionDto::getDatasetName).contains(name);
    }

    @Test
    void 좌표계가_없는_래스터는_검수에서_불합격된다() throws Exception {
        String name = uniqueName("nocrs");
        createDataset(name, "RASTER");
        Path tif = GdalTestSupport.createGeoTiff(tempDir.resolve("nocrs.tif"), null, 0, 0, 1, 10, 10, (c, r) -> 1, null);

        JobDto job = uploadAndWait(name, "nocrs.tif", Files.readAllBytes(tif), true);

        assertThat(job.getStatus()).isEqualTo(JobStatus.SUCCEEDED);   // 파이프라인은 정상 종료
        assertThat(job.getVersionStatus()).isEqualTo(VersionStatus.REJECTED);
        List<ValidationResultDto> report = queryService.getValidationResults(name, 1);
        assertThat(report).filteredOn(r -> !r.isPassed()).extracting(ValidationResultDto::getRuleCode)
                .containsExactly("RASTER_CRS");
        assertThat(datasetService.findByName(name).getActiveVersionNo()).isNull();
    }

    @Test
    void 래스터가_아닌_파일은_READABLE_규칙으로_불합격() throws Exception {
        String name = uniqueName("broken");
        createDataset(name, "RASTER");

        JobDto job = uploadAndWait(name, "broken.tif", "not a tiff".getBytes(StandardCharsets.UTF_8), false);

        assertThat(job.getVersionStatus()).isEqualTo(VersionStatus.REJECTED);
        assertThat(queryService.getValidationResults(name, 1)).extracting(ValidationResultDto::getRuleCode)
                .containsExactly("READABLE");
    }

    @Test
    void 같은_파일을_다시_올리면_중복으로_불합격() throws Exception {
        String name = uniqueName("dup");
        createDataset(name, "RASTER");
        byte[] dem = seoulDem("dup.tif");

        uploadAndWait(name, "dem.tif", dem, false);
        JobDto second = uploadAndWait(name, "dem-copy.tif", dem, false);

        assertThat(second.getVersionStatus()).isEqualTo(VersionStatus.REJECTED);
        assertThat(queryService.getValidationResults(name, 2)).filteredOn(r -> !r.isPassed())
                .extracting(ValidationResultDto::getRuleCode).containsExactly("DUPLICATE_FILE");
    }

    @Test
    void 도로망을_PostGIS에_적재하고_버전_배포와_롤백() throws Exception {
        String name = uniqueName("roads");
        createDataset(name, "ROAD_NETWORK");

        // v1: 3x3 격자 (링크 12개), v2: 4x4 격자 (링크 24개)
        JobDto v1Job = uploadAndWait(name, "v1.geojson", geojson(TestGeoJson.gridRoads(127.02, 37.49, 0.002, 3)), false);
        JobDto v2Job = uploadAndWait(name, "v2.geojson", geojson(TestGeoJson.gridRoads(127.02, 37.49, 0.002, 4)), false);
        assertThat(v1Job.getVersionStatus()).isEqualTo(VersionStatus.PROCESSED);
        assertThat(v2Job.getVersionStatus()).isEqualTo(VersionStatus.PROCESSED);

        long v1Id = datasetService.findVersion(name, 1).getId();
        long v2Id = datasetService.findVersion(name, 2).getId();
        assertThat(roadNetworkRepository.countLinks(v1Id)).isEqualTo(12);
        assertThat(roadNetworkRepository.countLinks(v2Id)).isEqualTo(24);
        assertThat(datasetService.findVersion(name, 1).getFeatureCount()).isEqualTo(12);

        // v1 배포 -> v2 배포 (v1 은 ARCHIVED) -> 롤백 (v1 재배포, v2 ARCHIVED)
        mockMvc.perform(post("/api/datasets/{name}/versions/1/publish", name)).andExpect(status().isOk());
        mockMvc.perform(post("/api/datasets/{name}/versions/2/publish", name)).andExpect(status().isOk());
        assertThat(datasetService.findVersion(name, 1).getStatus()).isEqualTo(VersionStatus.ARCHIVED);
        assertThat(datasetService.findByName(name).getActiveVersionNo()).isEqualTo(2);

        mockMvc.perform(post("/api/datasets/{name}/rollback", name))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.versionNo").value(1));
        assertThat(datasetService.findByName(name).getActiveVersionNo()).isEqualTo(1);
        assertThat(datasetService.findVersion(name, 2).getStatus()).isEqualTo(VersionStatus.ARCHIVED);
    }

    @Test
    void 불합격_버전은_배포할_수_없다() throws Exception {
        String name = uniqueName("reject");
        createDataset(name, "ROAD_NETWORK");
        // 도로 데이터셋에 포인트만 있음 -> GEOMETRY_TYPE 불합격
        uploadAndWait(name, "points.geojson",
                geojson(TestGeoJson.collection(List.of(TestGeoJson.point("a", "cafe", 127.0, 37.5)))), false);

        mockMvc.perform(post("/api/datasets/{name}/versions/1/publish", name))
                .andExpect(status().isConflict());
    }

    @Test
    void 서버_재시작으로_중단된_작업은_복구_후_실패한_단계부터_재시도된다() throws Exception {
        String name = uniqueName("poi");
        createDataset(name, "POI");
        String pois = TestGeoJson.collection(List.of(
                TestGeoJson.point("강남역", "subway", 127.0276, 37.4979),
                TestGeoJson.point("역삼역", "subway", 127.0364, 37.5006)));
        JobDto first = uploadAndWait(name, "pois.geojson", geojson(pois), false);
        long versionId = datasetService.findVersion(name, 1).getId();

        // 가공 도중 서버가 죽은 상황을 재현
        jdbcTemplate.update("UPDATE processing_job SET status = 'RUNNING', stage = 'PROCESS' WHERE id = ?", first.getJobId());
        jdbcTemplate.update("UPDATE dataset_version SET status = 'PROCESSING' WHERE id = ?", versionId);

        pipelineRecovery.recoverInterruptedJobs();
        assertThat(queryService.getJob(first.getJobId()).getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(datasetService.findVersion(name, 1).getStatus()).isEqualTo(VersionStatus.FAILED);

        String body = mockMvc.perform(post("/api/jobs/{id}/retry", first.getJobId()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long retryJobId = ((Number) com.jayway.jsonpath.JsonPath.read(body, "$.data.jobId")).longValue();
        JobDto retried = awaitJob(retryJobId);

        assertThat(retried.getAttempt()).isEqualTo(2);
        assertThat(retried.getStage()).isEqualTo(PipelineStage.PROCESS);
        assertThat(retried.getVersionStatus()).isEqualTo(VersionStatus.PROCESSED);
        // 검수는 다시 하지 않고 가공 단계부터 재개
        List<VersionStatus> history = queryService.getHistory(name, 1).stream().map(StatusHistoryDto::getToStatus).toList();
        assertThat(history.subList(history.size() - 3, history.size()))
                .containsExactly(VersionStatus.FAILED, VersionStatus.PROCESSING, VersionStatus.PROCESSED);
        // 재적재해도 중복되지 않음 (멱등)
        Integer poiCount = jdbcTemplate.queryForObject("SELECT count(*) FROM poi WHERE version_id = ?", Integer.class, versionId);
        assertThat(poiCount).isEqualTo(2);
    }

    private byte[] seoulDem(String fileName) throws Exception {
        Path tif = GdalTestSupport.createGeoTiff(tempDir.resolve(fileName), 5186, 200_000, 550_000, 30,
                200, 200, (col, row) -> 20 + col + row, -9999.0);
        return Files.readAllBytes(tif);
    }

    private byte[] geojson(String json) {
        return json.getBytes(StandardCharsets.UTF_8);
    }
}
