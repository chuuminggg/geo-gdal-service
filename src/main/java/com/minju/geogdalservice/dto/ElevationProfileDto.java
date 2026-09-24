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
public class ElevationProfileDto {
    private String datasetName;
    private int versionNo;
    private double lengthM;
    // 누적 오르막 / 내리막 (m)
    private double ascentM;
    private double descentM;
    private Double minElevation;
    private Double maxElevation;
    private List<ProfilePoint> points;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ProfilePoint {
        private double distanceM;
        private double lon;
        private double lat;
        private Double elevation;
    }
}
