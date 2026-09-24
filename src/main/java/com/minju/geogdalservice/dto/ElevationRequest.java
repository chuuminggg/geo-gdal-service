package com.minju.geogdalservice.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 다건 고도 조회 / 고도 프로파일 요청
 * coordinates: [[lon, lat], ...]
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ElevationRequest {

    // 생략하면 geo.elevation.default-dataset 사용
    private String dataset;

    @NotEmpty
    @Size(max = 1000)
    private List<double[]> coordinates;

    // 프로파일 샘플 간격 (m)
    @Positive
    private Double intervalM;
}
