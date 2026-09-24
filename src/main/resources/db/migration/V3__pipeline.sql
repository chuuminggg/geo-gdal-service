-- =========================================================
-- 수집 -> 검수 -> 가공 -> 배포 파이프라인
-- =========================================================

-- 파이프라인 실행 단위(비동기 작업). 재시도 시 새 작업이 attempt+1 로 생성된다.
CREATE TABLE processing_job
(
    id            BIGSERIAL PRIMARY KEY,
    version_id    BIGINT      NOT NULL REFERENCES dataset_version (id) ON DELETE CASCADE,
    status        VARCHAR(20) NOT NULL,
    stage         VARCHAR(20),
    attempt       INTEGER     NOT NULL,
    auto_publish  BOOLEAN     NOT NULL DEFAULT FALSE,
    error_message TEXT,
    created_at    TIMESTAMP   NOT NULL,
    started_at    TIMESTAMP,
    finished_at   TIMESTAMP
);
CREATE INDEX idx_processing_job_version ON processing_job (version_id);
CREATE INDEX idx_processing_job_status ON processing_job (status);

-- 검수 규칙별 결과 (검수 리포트)
CREATE TABLE validation_result
(
    id         BIGSERIAL PRIMARY KEY,
    version_id BIGINT        NOT NULL REFERENCES dataset_version (id) ON DELETE CASCADE,
    rule_code  VARCHAR(50)   NOT NULL,
    severity   VARCHAR(10)   NOT NULL,
    passed     BOOLEAN       NOT NULL,
    message    VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP     NOT NULL
);
CREATE INDEX idx_validation_result_version ON validation_result (version_id);

-- 버전 상태 변경 이력 (감사 로그)
CREATE TABLE version_status_history
(
    id          BIGSERIAL PRIMARY KEY,
    version_id  BIGINT      NOT NULL REFERENCES dataset_version (id) ON DELETE CASCADE,
    from_status VARCHAR(20),
    to_status   VARCHAR(20) NOT NULL,
    reason      VARCHAR(1000),
    changed_at  TIMESTAMP   NOT NULL
);
CREATE INDEX idx_version_status_history_version ON version_status_history (version_id);

-- 파이프라인(raster_metadata)으로 대체된 초기 메타데이터 테이블
DROP TABLE metadata;
