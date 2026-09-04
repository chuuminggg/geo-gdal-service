package com.minju.geogdalservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PixelValueDto {
    private String fileName;
    private double lat;
    private double lon;
    private int pixelX;   // 열(column)
    private int pixelY;   // 행(row)
    private List<BandValue> bands;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BandValue {
        private int band;
        private String dataType;
        private Double value;     // NoData이면 null
        private boolean noData;
    }
}
