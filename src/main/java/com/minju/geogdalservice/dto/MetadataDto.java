package com.minju.geogdalservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetadataDto {
    private String fileName;
    private int width;
    private int height;
    private int bandCount;

    // GDAL 추출 정보
    private String driver;
    private String dataType;
    private String crs;
    private Double pixelSizeX;
    private Double pixelSizeY;
    private Double minX;
    private Double minY;
    private Double maxX;
    private Double maxY;
}
