package com.minju.geogdalservice.controller;

import com.minju.geogdalservice.common.dto.CommonResponse;
import com.minju.geogdalservice.dto.ElevationDto;
import com.minju.geogdalservice.dto.ElevationProfileDto;
import com.minju.geogdalservice.dto.ElevationRequest;
import com.minju.geogdalservice.service.ElevationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/elevation")
@RequiredArgsConstructor
public class ElevationController {

    private final ElevationService elevationService;

    /**
     * 단일 지점 고도 (쌍선형 보간)
     */
    @GetMapping
    public CommonResponse<ElevationDto> elevation(@RequestParam double lon, @RequestParam double lat,
                                                  @RequestParam(required = false) String dataset) {
        return CommonResponse.success(elevationService.elevation(dataset, lon, lat));
    }

    /**
     * 다건 고도 조회 (최대 1000개)
     */
    @PostMapping("/batch")
    public CommonResponse<List<ElevationDto>> batch(@Valid @RequestBody ElevationRequest request) {
        return CommonResponse.success(elevationService.elevations(request.getDataset(), request.getCoordinates()));
    }

    /**
     * 경로 고도 프로파일 (일정 간격 샘플링 + 누적 오르막/내리막)
     */
    @PostMapping("/profile")
    public CommonResponse<ElevationProfileDto> profile(@Valid @RequestBody ElevationRequest request) {
        return CommonResponse.success(elevationService.profile(
                request.getDataset(), request.getCoordinates(), request.getIntervalM()));
    }
}
