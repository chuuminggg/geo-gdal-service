package com.minju.geogdalservice.common.exception;

// 오브젝트 스토리지(S3) 또는 원격 래스터 읽기 실패 - 클라이언트 요청 문제가 아닌 인프라 장애
public class StorageException extends RuntimeException {
    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }

    public StorageException(String message) {
        super(message);
    }
}
