package com.minju.geogdalservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public class CoordinateTransformDto {

    // sourceCrs/targetCrs: "EPSG:4326", "EPSG:5186", WKT, PROJ 문자열
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Request {
        @NotBlank
        private String sourceCrs;
        @NotBlank
        private String targetCrs;
        @NotEmpty
        @Size(max = 10000)
        private List<@Valid @NotNull Point> points;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private String sourceCrs;
        private String targetCrs;
        private List<Point> points;
    }

    // x = 경도/Easting, y = 위도/Northing
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Point {
        @NotNull
        private Double x;
        @NotNull
        private Double y;
        private Double z;
    }
}
