package com.minju.geogdalservice.pipeline;

import com.minju.geogdalservice.common.exception.NotFoundException;
import com.minju.geogdalservice.entity.*;
import com.minju.geogdalservice.repository.ProcessingJobRepository;
import com.minju.geogdalservice.service.DatasetService;
import com.minju.geogdalservice.service.S3Service;
import com.minju.geogdalservice.util.Checksums;
import com.minju.geogdalservice.util.TempWorkspace;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 수집: 업로드 파일을 S3 raw/ 영역에 보관하고 새 버전 + 파이프라인 작업을 등록한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IngestService {

    private static final Map<String, String> CONTENT_TYPES = Map.of(
            ".tif", "image/tiff",
            ".tiff", "image/tiff",
            ".geojson", "application/geo+json",
            ".json", "application/geo+json",
            ".zip", "application/zip",
            ".gpkg", "application/geopackage+sqlite3");

    private final DatasetService datasetService;
    private final PipelineRunner pipelineRunner;
    private final PipelineStateService stateService;
    private final ProcessingJobRepository jobRepository;
    private final S3Service s3Service;

    @Value("${aws.s3.bucket.name}")
    private String bucketName;

    public ProcessingJob upload(String datasetName, MultipartFile file, boolean autoPublish) {
        Dataset dataset = datasetService.getDataset(datasetName);
        String originalFileName = file.getOriginalFilename() == null ? "unknown" : file.getOriginalFilename();
        String extension = extension(originalFileName);

        if (file.isEmpty()) {
            throw new IllegalArgumentException("업로드할 파일이 없습니다.");
        }
        var allowed = pipelineRunner.processor(dataset.getType()).allowedExtensions();
        if (!allowed.contains(extension)) {
            throw new IllegalArgumentException("%s 데이터셋은 %s 파일만 업로드할 수 있습니다."
                    .formatted(dataset.getType(), allowed));
        }

        try (TempWorkspace workspace = TempWorkspace.create("ingest-")) {
            // 디스크에 저장하면서 체크섬 계산 (파일 전체를 메모리에 올리지 않음)
            Path local = workspace.resolve("upload" + extension);
            String checksum = Checksums.copyWithSha256(file.getInputStream(), local);
            long size = Files.size(local);

            // S3 업로드는 DB 트랜잭션 밖에서 수행해 커넥션을 오래 점유하지 않는다.
            // (DB 등록이 실패하면 raw 객체만 남으며, 이는 버전과 연결되지 않은 고아 객체로 정리 대상)
            String rawKey = "raw/%s/%s/%s".formatted(datasetName, UUID.randomUUID(), sanitize(originalFileName));
            s3Service.upload(bucketName, rawKey, local, CONTENT_TYPES.getOrDefault(extension, "application/octet-stream"));

            ProcessingJob job = stateService.registerVersion(dataset.getId(), originalFileName, rawKey, checksum, size, autoPublish);
            log.info("Ingested: dataset={}, file={}, size={}, job={}", datasetName, originalFileName, size, job.getId());
            return job;
        } catch (IOException e) {
            throw new UncheckedIOException("업로드 파일 처리 중 오류가 발생했습니다.", e);
        }
    }

    /**
     * 실패한 작업 재시도. 실패한 단계부터 다시 수행한다.
     */
    @Transactional
    public ProcessingJob retry(long jobId) {
        ProcessingJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new NotFoundException("작업을 찾을 수 없습니다: " + jobId));
        if (job.getStatus() != JobStatus.FAILED) {
            throw new IllegalStateException("실패한 작업만 재시도할 수 있습니다. (현재: " + job.getStatus() + ")");
        }
        DatasetVersion version = job.getVersion();
        ProcessingJob latest = jobRepository.findFirstByVersionIdOrderByIdDesc(version.getId()).orElseThrow();
        if (!latest.getId().equals(job.getId())) {
            throw new IllegalStateException("해당 버전의 가장 최근 작업만 재시도할 수 있습니다. (최근 작업: " + latest.getId() + ")");
        }
        if (version.getStatus() != VersionStatus.FAILED && version.getStatus() != VersionStatus.PROCESSED) {
            throw new IllegalStateException("재시도할 수 없는 버전 상태입니다: " + version.getStatus());
        }
        return stateService.queueJob(version, job.getAttempt() + 1, job.isAutoPublish(), job.getStage());
    }

    private static String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot).toLowerCase(Locale.ROOT);
    }

    // S3 키에 안전한 문자만 남김
    private static String sanitize(String fileName) {
        return fileName.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
