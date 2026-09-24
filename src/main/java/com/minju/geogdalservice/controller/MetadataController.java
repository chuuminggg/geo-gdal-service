package com.minju.geogdalservice.controller;


import com.minju.geogdalservice.common.dto.CommonResponse;
import com.minju.geogdalservice.dto.MetadataDto;
import com.minju.geogdalservice.service.MetadataService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/metadata")
@RequiredArgsConstructor
public class MetadataController {

    private final MetadataService metadataService;

    @PostMapping
    public CommonResponse<Void> save(@RequestBody MetadataDto dto) {
        metadataService.saveMetadata(dto);
        return CommonResponse.success(null);
    }

    @GetMapping
    public CommonResponse<List<MetadataDto>> search(
            @RequestParam(required = false) String fileName,
            @RequestParam(required = false) Integer bandCount,
            @RequestParam(required = false) Integer width
    ) {
        return CommonResponse.success(metadataService.search(fileName, bandCount, width));
    }

}