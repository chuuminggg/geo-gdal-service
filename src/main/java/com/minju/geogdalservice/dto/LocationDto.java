package com.minju.geogdalservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public class LocationDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Request {
        @NotBlank
        private String name;
        private String category;
        @NotNull
        @DecimalMin("-90") @DecimalMax("90")
        private Double latitude;
        @NotNull
        @DecimalMin("-180") @DecimalMax("180")
        private Double longitude;
        private String description;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Response {
        private Long id;
        private String name;
        private String category;
        private double latitude;
        private double longitude;
        private String description;
        private Double distanceMeters;   // 반경 검색 시에만 포함
    }
}
