package com.minju.geogdalservice.pipeline;

import com.minju.geogdalservice.common.exception.NotFoundException;
import com.minju.geogdalservice.entity.*;
import com.minju.geogdalservice.gis.RasterInfo;
import com.minju.geogdalservice.repository.*;
import com.minju.geogdalservice.validation.RuleResult;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * 파이프라인 상태 변경을 담당. 각 메서드가 짧은 트랜잭션 하나이며,
 * 오래 걸리는 GDAL/S3 작업은 이 서비스 밖(트랜잭션 밖)에서 수행한다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PipelineStateService {

    private final DatasetRepository datasetRepository;
    private final DatasetVersionRepository versionRepository;
    private final ProcessingJobRepository jobRepository;
    private final ValidationResultRepository validationResultRepository;
    private final RasterMetadataRepository rasterMetadataRepository;
    private final VersionStatusHistoryRepository historyRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 업로드된 파일을 새 버전으로 등록하고 파이프라인 작업을 큐에 넣는다.
     */
    public ProcessingJob registerVersion(Long datasetId, String originalFileName, String rawKey,
                                         String checksum, long fileSize, boolean autoPublish) {
        // 동시에 같은 데이터셋에 업로드되어도 버전 번호가 겹치지 않도록 잠금
        Dataset dataset = datasetRepository.findByIdForUpdate(datasetId)
                .orElseThrow(() -> new NotFoundException("데이터셋을 찾을 수 없습니다: " + datasetId));

        DatasetVersion version = versionRepository.save(DatasetVersion.builder()
                .dataset(dataset)
                .versionNo(versionRepository.findMaxVersionNo(datasetId) + 1)
                .originalFileName(originalFileName)
                .rawKey(rawKey)
                .checksum(checksum)
                .fileSize(fileSize)
                .build());
        recordHistory(version, null, VersionStatus.UPLOADED, "업로드: " + originalFileName);

        return queueJob(version, 1, autoPublish, null);
    }

    public ProcessingJob queueJob(DatasetVersion version, int attempt, boolean autoPublish, PipelineStage resumeStage) {
        ProcessingJob job = jobRepository.save(ProcessingJob.builder()
                .version(version)
                .attempt(attempt)
                .autoPublish(autoPublish)
                .stage(resumeStage)
                .build());
        // 커밋 이후 PipelineRunner 가 비동기로 실행 (@TransactionalEventListener AFTER_COMMIT)
        eventPublisher.publishEvent(new PipelineEvents.JobQueued(job.getId()));
        return job;
    }

    public JobContext startJob(long jobId) {
        ProcessingJob job = getJob(jobId);
        job.start();
        DatasetVersion v = job.getVersion();
        return new JobContext(job.getId(), v.getId(), v.getDataset().getName(), v.getDataset().getType(),
                v.getVersionNo(), v.getRawKey(), v.getOriginalFileName(), v.getStatus(), job.getStage(),
                job.isAutoPublish());
    }

    public void enterStage(long jobId, PipelineStage stage) {
        getJob(jobId).enterStage(stage);
    }

    public void enterStage(long jobId, PipelineStage stage, VersionStatus versionStatus, String reason) {
        ProcessingJob job = getJob(jobId);
        job.enterStage(stage);
        transition(job.getVersion(), versionStatus, reason);
    }

    // 동일 데이터셋에 같은 파일이 이미 있는지 (불합격 버전 제외)
    @Transactional(readOnly = true)
    public boolean isDuplicate(long versionId) {
        DatasetVersion v = getVersion(versionId);
        return versionRepository.existsByDatasetIdAndChecksumAndIdNotAndStatusNotIn(
                v.getDataset().getId(), v.getChecksum(), v.getId(), Set.of(VersionStatus.REJECTED));
    }

    /**
     * 검수 결과 저장 + VALIDATED / REJECTED 전이를 한 트랜잭션으로 처리
     */
    public void saveValidation(long versionId, ValidationOutcome outcome, List<RuleResult> results, boolean rejected) {
        DatasetVersion version = getVersion(versionId);

        validationResultRepository.deleteByVersionId(versionId);
        validationResultRepository.saveAll(results.stream()
                .map(r -> ValidationResult.builder()
                        .versionId(versionId)
                        .ruleCode(r.code())
                        .severity(r.severity())
                        .passed(r.passed())
                        .message(r.message().length() > 1000 ? r.message().substring(0, 1000) : r.message())
                        .build())
                .toList());

        version.updateSpatialInfo(outcome.footprint(), outcome.featureCount());
        if (outcome.rasterInfo() != null && !rasterMetadataRepository.existsById(versionId)) {
            rasterMetadataRepository.save(toRasterMetadata(version, outcome.rasterInfo()));
        }

        if (rejected) {
            String reason = results.stream().filter(RuleResult::isBlocking)
                    .map(r -> r.code() + ": " + r.message())
                    .reduce((a, b) -> a + " / " + b).orElse("검수 불합격");
            transition(version, VersionStatus.REJECTED, reason);
            version.recordError(reason);
        } else {
            transition(version, VersionStatus.VALIDATED, "검수 통과");
        }
    }

    public void markProcessed(long versionId, String processedKey) {
        DatasetVersion version = getVersion(versionId);
        version.assignProcessedKey(processedKey);
        transition(version, VersionStatus.PROCESSED, "가공 완료");
    }

    public void completeJob(long jobId) {
        getJob(jobId).succeed();
    }

    /**
     * 시스템 오류로 작업 실패. 진행 중이던 버전은 FAILED 로 바꿔 재시도할 수 있게 한다.
     */
    public void failJob(long jobId, String message) {
        ProcessingJob job = getJob(jobId);
        job.fail(message);
        DatasetVersion version = job.getVersion();
        if (version.getStatus().canTransitionTo(VersionStatus.FAILED)) {
            transition(version, VersionStatus.FAILED, message);
            version.recordError(message);
        }
    }

    public void transition(DatasetVersion version, VersionStatus target, String reason) {
        VersionStatus from = version.getStatus();
        version.changeStatus(target);
        recordHistory(version, from, target, reason);
    }

    private void recordHistory(DatasetVersion version, VersionStatus from, VersionStatus to, String reason) {
        historyRepository.save(VersionStatusHistory.builder()
                .versionId(version.getId())
                .fromStatus(from)
                .toStatus(to)
                .reason(reason != null && reason.length() > 1000 ? reason.substring(0, 1000) : reason)
                .build());
    }

    private ProcessingJob getJob(long jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new NotFoundException("작업을 찾을 수 없습니다: " + jobId));
    }

    private DatasetVersion getVersion(long versionId) {
        return versionRepository.findById(versionId)
                .orElseThrow(() -> new NotFoundException("버전을 찾을 수 없습니다: " + versionId));
    }

    private RasterMetadata toRasterMetadata(DatasetVersion version, RasterInfo info) {
        return RasterMetadata.builder()
                .version(version)
                .width(info.width())
                .height(info.height())
                .bandCount(info.bandCount())
                .dataType(info.dataType())
                .epsg(info.epsg())
                .srsWkt(info.srsWkt())
                .geoTransform(info.geoTransform())
                .pixelSizeX(info.pixelSizeX())
                .pixelSizeY(info.pixelSizeY())
                .resolutionM(info.resolutionM())
                .nodataValue(info.noDataValue())
                .nodataRatio(info.noDataRatio())
                .minValue(info.minValue())
                .maxValue(info.maxValue())
                .build();
    }
}
