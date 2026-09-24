package com.minju.geogdalservice.common.handler;


import com.minju.geogdalservice.common.dto.CommonResponse;
import com.minju.geogdalservice.common.exception.GdalUnavailableException;
import com.minju.geogdalservice.common.exception.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.sql.SQLException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<CommonResponse<?>> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("GlobalExceptionHandler IllegalArgumentException occurred: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(CommonResponse.error(400, e.getMessage()));
    }

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class,
            MethodArgumentTypeMismatchException.class,
            MethodArgumentNotValidException.class
    })
    public ResponseEntity<CommonResponse<?>> handleBadRequest(Exception e) {
        log.warn("GlobalExceptionHandler bad request: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(CommonResponse.error(400, e.getMessage()));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<CommonResponse<?>> handleNotFoundException(NotFoundException e) {
        log.warn("GlobalExceptionHandler NotFoundException occurred: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(CommonResponse.error(404, e.getMessage()));
    }

    // 허용되지 않은 상태 전이 등 현재 리소스 상태와 충돌하는 요청
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<CommonResponse<?>> handleIllegalStateException(IllegalStateException e) {
        log.warn("GlobalExceptionHandler IllegalStateException occurred: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(CommonResponse.error(409, e.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<CommonResponse<?>> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e) {
        log.warn("GlobalExceptionHandler MaxUploadSizeExceededException occurred: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(CommonResponse.error(413, "업로드 가능한 파일 크기를 초과했습니다."));
    }

    @ExceptionHandler(GdalUnavailableException.class)
    public ResponseEntity<CommonResponse<?>> handleGdalUnavailableException(GdalUnavailableException e) {
        log.error("GlobalExceptionHandler GdalUnavailableException occurred: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(CommonResponse.error(503, e.getMessage()));
    }

    @ExceptionHandler(NullPointerException.class)
    public ResponseEntity<CommonResponse<?>> handleNullPointerException(NullPointerException e) {
        log.error("GlobalExceptionHandler NullPointerException occurred: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(CommonResponse.error(500));
    }

    @ExceptionHandler(SQLException.class)
    public ResponseEntity<CommonResponse<?>> handleSQLException(SQLException e) {
        log.error("GlobalExceptionHandler SQLException occurred: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(CommonResponse.error(500));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<CommonResponse<?>> handleRuntimeException(RuntimeException e) {
        log.error("GlobalExceptionHandler RuntimeException occurred: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(CommonResponse.error(500));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<CommonResponse<?>> handleGeneralException(Exception e) {
        log.error("GlobalExceptionHandler GeneralException occurred: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(CommonResponse.error(500));
    }
}
