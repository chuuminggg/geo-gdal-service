package com.minju.geogdalservice.pipeline;

import com.minju.geogdalservice.entity.DatasetType;

public final class PipelineEvents {

    private PipelineEvents() {
    }

    // 작업이 생성되어 커밋되면 비동기로 파이프라인을 시작한다
    public record JobQueued(Long jobId) {
    }

    // 데이터셋의 서비스 버전이 바뀜 (경로 그래프 등 캐시 갱신 트리거)
    public record DatasetPublished(Long datasetId, String datasetName, DatasetType type, Long versionId) {
    }
}
