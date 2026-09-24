package com.minju.geogdalservice.dto;

import com.minju.geogdalservice.entity.JobStatus;
import com.minju.geogdalservice.entity.PipelineStage;
import com.minju.geogdalservice.entity.ProcessingJob;
import com.minju.geogdalservice.entity.VersionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobDto {
    private Long jobId;
    private String datasetName;
    private Integer versionNo;
    private VersionStatus versionStatus;
    private JobStatus status;
    private PipelineStage stage;
    private int attempt;
    private boolean autoPublish;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;

    public static JobDto from(ProcessingJob job) {
        return JobDto.builder()
                .jobId(job.getId())
                .datasetName(job.getVersion().getDataset().getName())
                .versionNo(job.getVersion().getVersionNo())
                .versionStatus(job.getVersion().getStatus())
                .status(job.getStatus())
                .stage(job.getStage())
                .attempt(job.getAttempt())
                .autoPublish(job.isAutoPublish())
                .errorMessage(job.getErrorMessage())
                .createdAt(job.getCreatedAt())
                .startedAt(job.getStartedAt())
                .finishedAt(job.getFinishedAt())
                .build();
    }
}
