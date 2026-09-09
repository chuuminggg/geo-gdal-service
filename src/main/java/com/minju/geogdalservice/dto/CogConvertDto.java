package com.minju.geogdalservice.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public class CogConvertDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Request {
        @NotBlank
        private String fileName;

        // 결과 파일명 (미지정 시 "{원본}_cog.tif")
        private String outputFileName;

        @Pattern(regexp = "(?i)NONE|LZW|DEFLATE|ZSTD|JPEG|WEBP|LERC",
                message = "compression must be one of NONE, LZW, DEFLATE, ZSTD, JPEG, WEBP, LERC")
        private String compression;

        @Min(64) @Max(4096)
        private Integer blockSize;

        // 결과 파일이 이미 있으면 덮어쓸지 여부
        private boolean overwrite;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private String sourceFileName;
        private String outputFileName;
        private String compression;
        private int blockSize;
        private long sourceSize;
        private long outputSize;
        private String layout;       // GDAL이 인식한 레이아웃 ("COG"이면 유효한 COG)
        private int overviewCount;
        private long elapsedMillis;
    }
}
