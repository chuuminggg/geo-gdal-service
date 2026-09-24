package com.minju.geogdalservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CoordinateTransformDto {
    private int from;
    private int to;
    private List<double[]> coordinates;
}
