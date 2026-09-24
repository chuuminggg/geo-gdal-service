package com.minju.geogdalservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadDto {
    private String fileName;
    private String originalFileName;
    private long fileSize;
    private String contentType;
    private String s3Key;
    private String s3Bucket;
}

