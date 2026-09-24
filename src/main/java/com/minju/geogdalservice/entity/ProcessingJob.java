package com.minju.geogdalservice.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "processing_job")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessingJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "version_id")
    private DatasetVersion version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobStatus status;

    // 현재(또는 실패한) 단계
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private PipelineStage stage;

    @Column(nullable = false)
    private int attempt;

    @Column(nullable = false)
    private boolean autoPublish;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;

    @Builder
    public ProcessingJob(DatasetVersion version, int attempt, boolean autoPublish, PipelineStage stage) {
        this.version = version;
        this.attempt = attempt;
        this.autoPublish = autoPublish;
        this.stage = stage;
        this.status = JobStatus.QUEUED;
    }

    public void start() {
        this.status = JobStatus.RUNNING;
        this.startedAt = LocalDateTime.now();
    }

    public void enterStage(PipelineStage stage) {
        this.stage = stage;
    }

    public void succeed() {
        this.status = JobStatus.SUCCEEDED;
        this.finishedAt = LocalDateTime.now();
    }

    public void fail(String message) {
        this.status = JobStatus.FAILED;
        this.errorMessage = message;
        this.finishedAt = LocalDateTime.now();
    }
}
