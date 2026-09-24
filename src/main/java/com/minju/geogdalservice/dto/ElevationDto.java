package com.minju.geogdalservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ElevationDto {
    private double lon;
    private double lat;
    // DEM 범위 밖이거나 NoData 면 null
    private Double elevation;
}
