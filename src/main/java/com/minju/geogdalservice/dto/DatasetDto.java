package com.minju.geogdalservice.dto;

import com.minju.geogdalservice.entity.Dataset;
import com.minju.geogdalservice.entity.DatasetType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DatasetDto {
    private Long id;
    private String name;
    private DatasetType type;
    private String description;
    private Integer activeVersionNo;
    private LocalDateTime createdAt;

    public static DatasetDto from(Dataset dataset) {
        return DatasetDto.builder()
                .id(dataset.getId())
                .name(dataset.getName())
                .type(dataset.getType())
                .description(dataset.getDescription())
                .activeVersionNo(dataset.getActiveVersion() == null ? null : dataset.getActiveVersion().getVersionNo())
                .createdAt(dataset.getCreatedAt())
                .build();
    }
}
