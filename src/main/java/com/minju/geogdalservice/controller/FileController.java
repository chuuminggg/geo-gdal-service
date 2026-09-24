package com.minju.geogdalservice.controller;

import com.minju.geogdalservice.common.dto.CommonResponse;
import com.minju.geogdalservice.dto.COGConversionDto;
import com.minju.geogdalservice.dto.FileUploadDto;
import com.minju.geogdalservice.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    /**
     * GeoTIFF 파일 업로드
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CommonResponse<FileUploadDto> uploadFile(
            @RequestParam("file") MultipartFile file) {
        
        FileUploadDto result = fileService.uploadFile(file);
        return CommonResponse.success("파일이 성공적으로 업로드되었습니다.", result);
    }

    /**
     * GeoTIFF를 COG로 변환
     */
    @PostMapping("/convert-to-cog")
    public CommonResponse<COGConversionDto> convertToCOG(
            @RequestParam("fileKey") String fileKey) {
        
        COGConversionDto result = fileService.convertToCOG(fileKey);
        return CommonResponse.success("COG 변환이 완료되었습니다.", result);
    }

    /**
     * 파일 업로드 및 COG 변환을 한 번에 처리
     */
    @PostMapping(value = "/upload-and-convert", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CommonResponse<COGConversionDto> uploadAndConvert(
            @RequestParam("file") MultipartFile file) {
        
        // 1. 파일 업로드
        FileUploadDto uploadResult = fileService.uploadFile(file);
        
        // 2. COG 변환
        COGConversionDto conversionResult = fileService.convertToCOG(uploadResult.getS3Key());
        return CommonResponse.success("파일 업로드 및 COG 변환이 완료되었습니다.", conversionResult);
    }
}

