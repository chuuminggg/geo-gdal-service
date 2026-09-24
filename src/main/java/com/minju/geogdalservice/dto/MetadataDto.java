package com.minju.geogdalservice.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MetadataDto {
    private String fileName;
    private int width;
    private int height;
    private int bandCount;
}