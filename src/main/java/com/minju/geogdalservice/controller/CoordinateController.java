package com.minju.geogdalservice.controller;

import com.minju.geogdalservice.common.dto.CommonResponse;
import com.minju.geogdalservice.dto.CoordinateTransformDto;
import com.minju.geogdalservice.dto.CoordinateTransformRequest;
import com.minju.geogdalservice.service.CoordinateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/coordinates")
@RequiredArgsConstructor
public class CoordinateController {

    private final CoordinateService coordinateService;

    /**
     * 단일 좌표 변환 (예: /api/coordinates/transform?x=127.0276&y=37.4979&from=4326&to=5179)
     */
    @GetMapping("/transform")
    public CommonResponse<CoordinateTransformDto> transform(@RequestParam double x, @RequestParam double y,
                                                            @RequestParam int from, @RequestParam int to) {
        List<double[]> result = coordinateService.transform(List.<double[]>of(new double[]{x, y}), from, to);
        return CommonResponse.success(new CoordinateTransformDto(from, to, result));
    }

    /**
     * 다건 좌표 변환
     */
    @PostMapping("/transform")
    public CommonResponse<CoordinateTransformDto> transformBatch(@Valid @RequestBody CoordinateTransformRequest request) {
        List<double[]> result = coordinateService.transform(request.getCoordinates(), request.getFrom(), request.getTo());
        return CommonResponse.success(new CoordinateTransformDto(request.getFrom(), request.getTo(), result));
    }
}
