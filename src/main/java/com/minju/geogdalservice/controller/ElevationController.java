package com.minju.geogdalservice.controller;

import com.minju.geogdalservice.common.dto.CommonResponse;
import com.minju.geogdalservice.dto.ElevationDto;
import com.minju.geogdalservice.dto.ElevationDto.Interpolation;
import com.minju.geogdalservice.service.ElevationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/elevation")
@RequiredArgsConstructor
public class ElevationController {

    private final ElevationService elevationService;

    // 단일 지점 고도 조회
    @GetMapping
    public CommonResponse<ElevationDto.Response> getElevation(
            @RequestParam String fileName,
            @RequestParam @DecimalMin(value = "-90", message = "lat must be >= -90")
            @DecimalMax(value = "90", message = "lat must be <= 90") double lat,
            @RequestParam @DecimalMin(value = "-180", message = "lon must be >= -180")
            @DecimalMax(value = "180", message = "lon must be <= 180") double lon,
            @RequestParam(defaultValue = "NEAREST") Interpolation interpolation,
            @RequestParam(required = false) Integer band
    ) {
        List<ElevationDto.Point> points = List.of(ElevationDto.Point.builder().lat(lat).lon(lon).build());
        return CommonResponse.success(elevationService.getElevations(fileName, points, interpolation, band));
    }

    // 다중 지점 고도 조회
    @PostMapping
    public CommonResponse<ElevationDto.Response> getElevations(@RequestBody @Valid ElevationDto.Request request) {
        return CommonResponse.success(elevationService.getElevations(
                request.getFileName(), request.getPoints(), request.getInterpolation(), request.getBand()));
    }
}
