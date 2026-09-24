package com.minju.geogdalservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PoiDto {
    private long id;
    private String datasetName;
    private String name;
    private String category;
    private String address;
    private double lon;
    private double lat;
    private double distanceM;
}
