package com.minju.geogdalservice.dto;

import com.minju.geogdalservice.entity.VersionStatus;
import com.minju.geogdalservice.entity.VersionStatusHistory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatusHistoryDto {
    private VersionStatus fromStatus;
    private VersionStatus toStatus;
    private String reason;
    private LocalDateTime changedAt;

    public static StatusHistoryDto from(VersionStatusHistory h) {
        return StatusHistoryDto.builder()
                .fromStatus(h.getFromStatus())
                .toStatus(h.getToStatus())
                .reason(h.getReason())
                .changedAt(h.getChangedAt())
                .build();
    }
}
