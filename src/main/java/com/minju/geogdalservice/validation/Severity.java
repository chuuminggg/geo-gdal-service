package com.minju.geogdalservice.validation;

public enum Severity {
    INFO,    // 통과
    WARN,    // 배포는 가능하지만 확인이 필요한 품질 이슈
    ERROR    // 불합격 (REJECTED)
}
