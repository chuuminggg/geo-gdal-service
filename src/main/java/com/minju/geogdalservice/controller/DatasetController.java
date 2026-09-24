package com.minju.geogdalservice.controller;

import com.minju.geogdalservice.common.dto.CommonResponse;
import com.minju.geogdalservice.dto.*;
import com.minju.geogdalservice.entity.DatasetType;
import com.minju.geogdalservice.pipeline.IngestService;
import com.minju.geogdalservice.pipeline.PipelineQueryService;
import com.minju.geogdalservice.pipeline.PublishService;
import com.minju.geogdalservice.service.DatasetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/datasets")
@RequiredArgsConstructor
public class DatasetController {

    private final DatasetService datasetService;
    private final IngestService ingestService;
    private final PublishService publishService;
    private final PipelineQueryService queryService;

    /**
     * 데이터셋 생성 (예: {"name":"seoul-dem","type":"RASTER"})
     */
    @PostMapping
    public CommonResponse<DatasetDto> create(@Valid @RequestBody DatasetCreateRequest request) {
        return CommonResponse.success(201, "데이터셋이 생성되었습니다.", datasetService.create(request));
    }

    @GetMapping
    public CommonResponse<List<DatasetDto>> findAll() {
        return CommonResponse.success(datasetService.findAll());
    }

    @GetMapping("/{name}")
    public CommonResponse<DatasetDto> findByName(@PathVariable String name) {
        return CommonResponse.success(datasetService.findByName(name));
    }

    /**
     * 수집: 새 버전 업로드. 검수 -> 가공은 비동기로 진행되며 반환된 jobId 로 진행 상황을 조회한다.
     *
     * @param autoPublish 검수·가공 성공 시 바로 서비스 버전으로 배포
     */
    @PostMapping(value = "/{name}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CommonResponse<JobDto> upload(@PathVariable String name,
                                         @RequestParam("file") MultipartFile file,
                                         @RequestParam(defaultValue = "false") boolean autoPublish) {
        long jobId = ingestService.upload(name, file, autoPublish).getId();
        return CommonResponse.success(202, "업로드되었습니다. 검수/가공이 진행됩니다.", queryService.getJob(jobId));
    }

    @GetMapping("/{name}/versions")
    public CommonResponse<List<DatasetVersionDto>> findVersions(@PathVariable String name) {
        return CommonResponse.success(datasetService.findVersions(name));
    }

    @GetMapping("/{name}/versions/{versionNo}")
    public CommonResponse<DatasetVersionDto> findVersion(@PathVariable String name, @PathVariable int versionNo) {
        return CommonResponse.success(datasetService.findVersion(name, versionNo));
    }

    // 검수 리포트
    @GetMapping("/{name}/versions/{versionNo}/validation")
    public CommonResponse<List<ValidationResultDto>> validation(@PathVariable String name, @PathVariable int versionNo) {
        return CommonResponse.success(queryService.getValidationResults(name, versionNo));
    }

    // 상태 변경 이력
    @GetMapping("/{name}/versions/{versionNo}/history")
    public CommonResponse<List<StatusHistoryDto>> history(@PathVariable String name, @PathVariable int versionNo) {
        return CommonResponse.success(queryService.getHistory(name, versionNo));
    }

    @GetMapping("/{name}/versions/{versionNo}/jobs")
    public CommonResponse<List<JobDto>> jobs(@PathVariable String name, @PathVariable int versionNo) {
        return CommonResponse.success(queryService.getJobs(name, versionNo));
    }

    /**
     * 배포: 해당 버전을 서비스 버전으로 전환 (ARCHIVED 버전을 지정하면 롤백)
     */
    @PostMapping("/{name}/versions/{versionNo}/publish")
    public CommonResponse<DatasetVersionDto> publish(@PathVariable String name, @PathVariable int versionNo) {
        publishService.publish(name, versionNo);
        return CommonResponse.success("배포되었습니다.", datasetService.findVersion(name, versionNo));
    }

    /**
     * 직전 서비스 버전으로 롤백
     */
    @PostMapping("/{name}/rollback")
    public CommonResponse<DatasetVersionDto> rollback(@PathVariable String name) {
        int versionNo = publishService.rollback(name).getVersionNo();
        return CommonResponse.success("롤백되었습니다.", datasetService.findVersion(name, versionNo));
    }

    /**
     * 영역(bbox)과 겹치는 배포 중인 데이터 검색
     */
    @GetMapping("/search")
    public CommonResponse<List<DatasetVersionDto>> searchByBbox(
            @RequestParam double minLon, @RequestParam double minLat,
            @RequestParam double maxLon, @RequestParam double maxLat,
            @RequestParam(required = false) DatasetType type) {
        return CommonResponse.success(datasetService.searchByBbox(minLon, minLat, maxLon, maxLat, type));
    }
}
