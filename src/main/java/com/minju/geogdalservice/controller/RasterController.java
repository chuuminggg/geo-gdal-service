package com.minju.geogdalservice.controller;

import com.minju.geogdalservice.common.dto.CommonResponse;
import com.minju.geogdalservice.dto.PixelValueDto;
import com.minju.geogdalservice.service.RasterQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rasters")
@RequiredArgsConstructor
public class RasterController {

    private final RasterQueryService rasterQueryService;

    /**
     * 위경도 기반 픽셀 값 추출 (배포 중인 버전의 COG 에서 조회)
     */
    @GetMapping("/{dataset}/value")
    public CommonResponse<PixelValueDto> pixelValue(@PathVariable String dataset,
                                                    @RequestParam double lon, @RequestParam double lat) {
        return CommonResponse.success(rasterQueryService.pixelValues(dataset, lon, lat));
    }
}
