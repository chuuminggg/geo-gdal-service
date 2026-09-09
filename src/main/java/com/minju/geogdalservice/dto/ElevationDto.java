package com.minju.geogdalservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public class ElevationDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Request {
        @NotBlank
        private String fileName;
        private Interpolation interpolation;
        @Min(1)
        private Integer band;
        @NotEmpty
        @Size(max = 10000)
        private List<@Valid @NotNull Point> points;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Point {
        @NotNull
        @DecimalMin("-90") @DecimalMax("90")
        private Double lat;
        @NotNull
        @DecimalMin("-180") @DecimalMax("180")
        private Double lon;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private String fileName;
        private int band;
        private String unit;
        private Interpolation interpolation;
        private List<Result> results;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Result {
        private double lat;
        private double lon;
        private Double elevation;   // OK가 아니면 null
        private Status status;
    }

    public enum Interpolation {
        NEAREST,
        BILINEAR
    }

    public enum Status {
        OK,
        NO_DATA,
        OUT_OF_BOUNDS
    }
}
