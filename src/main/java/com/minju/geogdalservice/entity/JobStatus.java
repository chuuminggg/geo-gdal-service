package com.minju.geogdalservice.entity;

public enum JobStatus {
    QUEUED,
    RUNNING,
    SUCCEEDED,   // 파이프라인이 끝까지 수행됨 (검수 불합격으로 종료된 경우 포함)
    FAILED       // 시스템 오류로 중단됨 -> 재시도 가능
}
