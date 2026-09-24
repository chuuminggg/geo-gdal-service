package com.minju.geogdalservice.controller;

import com.minju.geogdalservice.common.dto.CommonResponse;
import com.minju.geogdalservice.dto.DatasetCreateRequest;
import com.minju.geogdalservice.dto.DatasetDto;
import com.minju.geogdalservice.dto.DatasetVersionDto;
import com.minju.geogdalservice.entity.DatasetType;
import com.minju.geogdalservice.service.DatasetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/datasets")
@RequiredArgsConstructor
public class DatasetController {

    private final DatasetService datasetService;

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

    @GetMapping("/{name}/versions")
    public CommonResponse<List<DatasetVersionDto>> findVersions(@PathVariable String name) {
        return CommonResponse.success(datasetService.findVersions(name));
    }

    @GetMapping("/{name}/versions/{versionNo}")
    public CommonResponse<DatasetVersionDto> findVersion(@PathVariable String name, @PathVariable int versionNo) {
        return CommonResponse.success(datasetService.findVersion(name, versionNo));
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
