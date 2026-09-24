package com.minju.geogdalservice.dto;

import com.minju.geogdalservice.entity.RasterMetadata;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RasterMetadataDto {
    private int width;
    private int height;
    private int bandCount;
    private String dataType;
    private Integer epsg;
    private double[] geoTransform;
    private Double pixelSizeX;
    private Double pixelSizeY;
    private Double resolutionM;
    private Double nodataValue;
    private Double nodataRatio;
    private Double minValue;
    private Double maxValue;

    public static RasterMetadataDto from(RasterMetadata m) {
        return RasterMetadataDto.builder()
                .width(m.getWidth())
                .height(m.getHeight())
                .bandCount(m.getBandCount())
                .dataType(m.getDataType())
                .epsg(m.getEpsg())
                .geoTransform(m.getGeoTransform())
                .pixelSizeX(m.getPixelSizeX())
                .pixelSizeY(m.getPixelSizeY())
                .resolutionM(m.getResolutionM())
                .nodataValue(m.getNodataValue())
                .nodataRatio(m.getNodataRatio())
                .minValue(m.getMinValue())
                .maxValue(m.getMaxValue())
                .build();
    }
}
