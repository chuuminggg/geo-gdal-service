package com.minju.geogdalservice.pipeline;

import com.minju.geogdalservice.entity.DatasetType;
import com.minju.geogdalservice.entity.PipelineStage;
import com.minju.geogdalservice.entity.VersionStatus;

/**
 * 트랜잭션 밖(비동기 작업 스레드)에서 쓰기 위한 작업 정보 스냅샷.
 * 엔티티를 그대로 넘기면 지연 로딩/영속성 컨텍스트 문제가 생기므로 값만 복사한다.
 */
public record JobContext(
        long jobId,
        long versionId,
        String datasetName,
        DatasetType datasetType,
        int versionNo,
        String rawKey,
        String originalFileName,
        VersionStatus versionStatus,
        PipelineStage resumeStage,
        boolean autoPublish
) {
    public String extension() {
        int dot = originalFileName.lastIndexOf('.');
        return dot < 0 ? "" : originalFileName.substring(dot).toLowerCase(java.util.Locale.ROOT);
    }
}
