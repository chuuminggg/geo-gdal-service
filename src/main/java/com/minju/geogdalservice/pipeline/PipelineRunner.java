package com.minju.geogdalservice.pipeline;

import com.minju.geogdalservice.entity.DatasetType;
import com.minju.geogdalservice.entity.PipelineStage;
import com.minju.geogdalservice.entity.VersionStatus;
import com.minju.geogdalservice.service.S3Service;
import com.minju.geogdalservice.util.TempWorkspace;
import com.minju.geogdalservice.validation.RuleResult;
import com.minju.geogdalservice.validation.ValidationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 파이프라인 실행기: 검수(VALIDATE) -> 가공(PROCESS) -> (선택) 배포(PUBLISH)
 *
 * - 작업 생성 트랜잭션이 커밋된 뒤에만 실행된다. (커밋 전에 실행되면 작업을 못 찾는 문제 방지)
 * - 파이프라인 전용 스레드 풀에서 실행되어 업로드 API 는 바로 응답한다.
 * - 실패 시 실패한 단계를 기록해 두고, 재시도하면 그 단계부터 다시 수행한다.
 */
@Slf4j
@Component
public class PipelineRunner {

    private final PipelineStateService stateService;
    private final PublishService publishService;
    private final S3Service s3Service;
    private final Map<DatasetType, DatasetProcessor> processors = new EnumMap<>(DatasetType.class);

    @Value("${aws.s3.bucket.name}")
    private String bucketName;

    public PipelineRunner(PipelineStateService stateService, PublishService publishService, S3Service s3Service,
                          List<DatasetProcessor> processorList) {
        this.stateService = stateService;
        this.publishService = publishService;
        this.s3Service = s3Service;
        processorList.forEach(p -> processors.put(p.type(), p));
    }

    public DatasetProcessor processor(DatasetType type) {
        DatasetProcessor processor = processors.get(type);
        if (processor == null) {
            throw new IllegalStateException("처리기가 등록되지 않은 데이터 타입입니다: " + type);
        }
        return processor;
    }

    @Async("pipelineExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onJobQueued(PipelineEvents.JobQueued event) {
        run(event.jobId());
    }

    public void run(long jobId) {
        JobContext ctx = stateService.startJob(jobId);
        log.info("Pipeline started: job={}, dataset={}, v{}, status={}",
                jobId, ctx.datasetName(), ctx.versionNo(), ctx.versionStatus());

        try (TempWorkspace workspace = TempWorkspace.create("pipeline-")) {
            DatasetProcessor processor = processor(ctx.datasetType());
            VersionStatus status = ctx.versionStatus();
            Path rawFile = null;

            if (needsValidation(ctx)) {
                rawFile = downloadRaw(ctx, workspace);
                if (!validate(ctx, processor, rawFile)) {
                    stateService.completeJob(jobId);
                    log.info("Pipeline finished (rejected): job={}", jobId);
                    return;
                }
                status = VersionStatus.VALIDATED;
            }

            if (status == VersionStatus.VALIDATED || status == VersionStatus.FAILED) {
                if (rawFile == null) {
                    rawFile = downloadRaw(ctx, workspace);
                }
                stateService.enterStage(jobId, PipelineStage.PROCESS, VersionStatus.PROCESSING, "가공 시작");
                String processedKey = processor.process(ctx, rawFile, workspace);
                stateService.markProcessed(ctx.versionId(), processedKey);
            }

            if (ctx.autoPublish()) {
                stateService.enterStage(jobId, PipelineStage.PUBLISH);
                publishService.publish(ctx.datasetName(), ctx.versionNo());
            }

            stateService.completeJob(jobId);
            log.info("Pipeline finished: job={}, dataset={}, v{}", jobId, ctx.datasetName(), ctx.versionNo());

        } catch (Exception e) {
            log.error("Pipeline failed: job={}, dataset={}, v{}", jobId, ctx.datasetName(), ctx.versionNo(), e);
            stateService.failJob(jobId, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    // 신규 업로드이거나, 검수 단계에서 실패한 작업의 재시도
    private boolean needsValidation(JobContext ctx) {
        return ctx.versionStatus() == VersionStatus.UPLOADED
                || (ctx.versionStatus() == VersionStatus.FAILED && ctx.resumeStage() != PipelineStage.PROCESS);
    }

    private boolean validate(JobContext ctx, DatasetProcessor processor, Path rawFile) {
        stateService.enterStage(ctx.jobId(), PipelineStage.VALIDATE, VersionStatus.VALIDATING, "검수 시작");

        ValidationOutcome outcome = processor.validate(rawFile);
        List<RuleResult> results = new ArrayList<>(outcome.results());
        if (stateService.isDuplicate(ctx.versionId())) {
            results.add(RuleResult.error("DUPLICATE_FILE", "동일한 파일(SHA-256)이 이미 등록되어 있습니다."));
        }

        boolean rejected = ValidationService.hasBlockingError(results);
        stateService.saveValidation(ctx.versionId(), outcome, results, rejected);
        return !rejected;
    }

    private Path downloadRaw(JobContext ctx, TempWorkspace workspace) {
        Path rawFile = workspace.resolve("raw" + ctx.extension());
        s3Service.download(bucketName, ctx.rawKey(), rawFile);
        return rawFile;
    }
}
