package com.minju.geogdalservice.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * 데이터셋 버전의 파이프라인 상태.
 *
 * UPLOADED -> VALIDATING -> VALIDATED -> PROCESSING -> PROCESSED -> PUBLISHED <-> ARCHIVED
 *                        \-> REJECTED (검수 불합격, 종료 상태)
 * VALIDATING/PROCESSING 중 시스템 오류 -> FAILED -> (재시도) VALIDATING/PROCESSING
 */
public enum VersionStatus {
    UPLOADED,
    VALIDATING,
    VALIDATED,
    REJECTED,
    PROCESSING,
    PROCESSED,
    PUBLISHED,
    ARCHIVED,
    FAILED;

    private Set<VersionStatus> next;

    static {
        UPLOADED.next = EnumSet.of(VALIDATING);
        VALIDATING.next = EnumSet.of(VALIDATED, REJECTED, FAILED);
        VALIDATED.next = EnumSet.of(PROCESSING);
        REJECTED.next = EnumSet.noneOf(VersionStatus.class);
        PROCESSING.next = EnumSet.of(PROCESSED, FAILED);
        PROCESSED.next = EnumSet.of(PUBLISHED);
        PUBLISHED.next = EnumSet.of(ARCHIVED);
        ARCHIVED.next = EnumSet.of(PUBLISHED);   // 롤백: 이전 버전 재배포
        FAILED.next = EnumSet.of(VALIDATING, PROCESSING);
    }

    public boolean canTransitionTo(VersionStatus target) {
        return next.contains(target);
    }
}
