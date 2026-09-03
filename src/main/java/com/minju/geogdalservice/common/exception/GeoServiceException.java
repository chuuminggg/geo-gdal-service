package com.minju.geogdalservice.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

// 서비스 로직 예외 (HTTP 상태 코드 + 응답 메시지)
@Getter
public class GeoServiceException extends RuntimeException {

    private final HttpStatus status;

    public GeoServiceException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public GeoServiceException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public static GeoServiceException badRequest(String message) {
        return new GeoServiceException(HttpStatus.BAD_REQUEST, message);
    }

    public static GeoServiceException notFound(String message) {
        return new GeoServiceException(HttpStatus.NOT_FOUND, message);
    }
}
