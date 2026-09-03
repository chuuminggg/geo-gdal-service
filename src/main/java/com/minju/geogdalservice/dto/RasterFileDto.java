package com.minju.geogdalservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RasterFileDto {
    private String fileName;
    private long size;
    private Instant lastModified;
}
