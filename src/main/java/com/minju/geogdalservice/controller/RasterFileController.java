package com.minju.geogdalservice.controller;

import com.minju.geogdalservice.common.dto.CommonResponse;
import com.minju.geogdalservice.dto.RasterFileDto;
import com.minju.geogdalservice.service.RasterStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/api/rasters/files")
@RequiredArgsConstructor
public class RasterFileController {

    private final RasterStorageService rasterStorageService;

    // GeoTIFF 업로드
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CommonResponse<RasterFileDto> upload(@RequestPart("file") MultipartFile file) {
        return CommonResponse.success(rasterStorageService.store(file));
    }

    // 저장된 파일 목록
    @GetMapping
    public CommonResponse<List<RasterFileDto>> list() {
        return CommonResponse.success(rasterStorageService.list());
    }

    // 파일 다운로드
    @GetMapping("/download")
    public ResponseEntity<Resource> download(@RequestParam String fileName) {
        Path path = rasterStorageService.resolveExisting(fileName);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("image/tiff"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(path.getFileName().toString(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(new FileSystemResource(path));
    }
}
