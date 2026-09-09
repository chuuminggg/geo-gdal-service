package com.minju.geogdalservice.controller;

import com.minju.geogdalservice.common.dto.CommonResponse;
import com.minju.geogdalservice.dto.CogConvertDto;
import com.minju.geogdalservice.service.CogConvertService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rasters/cog")
@RequiredArgsConstructor
public class CogConvertController {

    private final CogConvertService cogConvertService;

    // GeoTIFF → COG 변환
    @PostMapping
    public CommonResponse<CogConvertDto.Response> convert(@RequestBody @Valid CogConvertDto.Request request) {
        return CommonResponse.success(cogConvertService.convert(request));
    }
}
