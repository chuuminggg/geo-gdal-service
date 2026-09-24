package com.minju.geogdalservice.common.exception;

// GDAL 네이티브 라이브러리가 로드되지 않아 공간 연산을 수행할 수 없는 경우
public class GdalUnavailableException extends RuntimeException {
    public GdalUnavailableException(String message) {
        super(message);
    }
}
