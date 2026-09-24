package com.minju.geogdalservice.service;

import java.nio.file.Path;

public interface S3Service {
    // 업로드 (파일 단위 스트리밍 업로드 - 전체를 메모리에 올리지 않음)
    void upload(String bucket, String key, Path file, String contentType);

    // 다운로드 (target 경로에 파일로 저장)
    void download(String bucket, String key, Path target);

    // S3 존재 여부 확인 로직 구현
    boolean exists(String bucket, String key);
}
