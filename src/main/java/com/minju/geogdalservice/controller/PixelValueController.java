package com.minju.geogdalservice.controller;

import com.minju.geogdalservice.common.dto.CommonResponse;
import com.minju.geogdalservice.dto.PixelValueDto;
import com.minju.geogdalservice.service.PixelValueService;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rasters/pixel")
@RequiredArgsConstructor
public class PixelValueController {

    private final PixelValueService pixelValueService;

    // 위경도 기반 픽셀 값 추출
    @GetMapping
    public CommonResponse<PixelValueDto> getPixelValue(
            @RequestParam String fileName,
            @RequestParam @DecimalMin(value = "-90", message = "lat must be >= -90")
            @DecimalMax(value = "90", message = "lat must be <= 90") double lat,
            @RequestParam @DecimalMin(value = "-180", message = "lon must be >= -180")
            @DecimalMax(value = "180", message = "lon must be <= 180") double lon,
            @RequestParam(required = false) Integer band
    ) {
        return CommonResponse.success(pixelValueService.getPixelValue(fileName, lat, lon, band));
    }
}
