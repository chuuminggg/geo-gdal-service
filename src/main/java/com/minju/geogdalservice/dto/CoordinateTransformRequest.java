package com.minju.geogdalservice.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CoordinateTransformRequest {

    @NotNull
    private Integer from;

    @NotNull
    private Integer to;

    // [[x, y], ...] (경위도는 [lon, lat] 순서)
    @NotEmpty
    private List<double[]> coordinates;
}
