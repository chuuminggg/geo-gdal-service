package com.minju.geogdalservice.entity;

import com.minju.geogdalservice.validation.Severity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "validation_result")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ValidationResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "version_id", nullable = false)
    private Long versionId;

    @Column(nullable = false, length = 50)
    private String ruleCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Severity severity;

    @Column(nullable = false)
    private boolean passed;

    @Column(nullable = false, length = 1000)
    private String message;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public ValidationResult(Long versionId, String ruleCode, Severity severity, boolean passed, String message) {
        this.versionId = versionId;
        this.ruleCode = ruleCode;
        this.severity = severity;
        this.passed = passed;
        this.message = message;
    }
}
