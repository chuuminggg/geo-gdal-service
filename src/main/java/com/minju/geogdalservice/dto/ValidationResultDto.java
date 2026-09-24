package com.minju.geogdalservice.dto;

import com.minju.geogdalservice.entity.ValidationResult;
import com.minju.geogdalservice.validation.Severity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationResultDto {
    private String ruleCode;
    private Severity severity;
    private boolean passed;
    private String message;

    public static ValidationResultDto from(ValidationResult r) {
        return ValidationResultDto.builder()
                .ruleCode(r.getRuleCode())
                .severity(r.getSeverity())
                .passed(r.isPassed())
                .message(r.getMessage())
                .build();
    }
}
