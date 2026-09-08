package com.minju.geogdalservice.controller;

import com.minju.geogdalservice.common.dto.CommonResponse;
import com.minju.geogdalservice.dto.CoordinateTransformDto;
import com.minju.geogdalservice.service.CoordinateTransformService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/coordinates/transform")
@RequiredArgsConstructor
public class CoordinateTransformController {

    private final CoordinateTransformService coordinateTransformService;

    // 단일 좌표 변환
    @GetMapping
    public CommonResponse<CoordinateTransformDto.Response> transformOne(
            @RequestParam String sourceCrs,
            @RequestParam String targetCrs,
            @RequestParam double x,
            @RequestParam double y
    ) {
        CoordinateTransformDto.Request request = CoordinateTransformDto.Request.builder()
                .sourceCrs(sourceCrs)
                .targetCrs(targetCrs)
                .points(List.of(CoordinateTransformDto.Point.builder().x(x).y(y).build()))
                .build();
        return CommonResponse.success(coordinateTransformService.transform(request));
    }

    // 다건 좌표 변환
    @PostMapping
    public CommonResponse<CoordinateTransformDto.Response> transform(
            @RequestBody @Valid CoordinateTransformDto.Request request
    ) {
        return CommonResponse.success(coordinateTransformService.transform(request));
    }
}
