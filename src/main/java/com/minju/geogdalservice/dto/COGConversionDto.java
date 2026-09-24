package com.minju.geogdalservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class COGConversionDto {
    private String originalFileKey;
    private String cogFileKey;
    private String s3Bucket;
    private boolean success;
    private String message;
    private long originalFileSize;
    private long cogFileSize;
}

