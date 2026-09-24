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
    private String datasetName;
    private int versionNo;
    private double lon;
    private double lat;
    private int pixelX;
    private int pixelY;
    // 좌표가 래스터 범위 안인지
    private boolean inside;
    // 밴드별 값 (NoData 는 null)
    private List<Double> values;
}
