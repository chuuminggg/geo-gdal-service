package com.minju.geogdalservice.common.handler;


import com.minju.geogdalservice.common.dto.CommonResponse;
import com.minju.geogdalservice.common.exception.GeoServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.sql.SQLException;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(GeoServiceException.class)
    public ResponseEntity<CommonResponse<?>> handleGeoServiceException(GeoServiceException e) {
        log.warn("GlobalExceptionHandler GeoServiceException occurred: {}", e.getMessage());
        return ResponseEntity.status(e.getStatus())
                .body(CommonResponse.error(e.getStatus().value(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<CommonResponse<?>> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        log.warn("GlobalExceptionHandler MethodArgumentNotValidException occurred: {}", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(CommonResponse.error(400, message));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<CommonResponse<?>> handleHandlerMethodValidationException(HandlerMethodValidationException e) {
        String message = e.getAllErrors().stream()
                .map(MessageSourceResolvable::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.warn("GlobalExceptionHandler HandlerMethodValidationException occurred: {}", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(CommonResponse.error(400, message));
    }

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<CommonResponse<?>> handleRequestParameterException(Exception e) {
        log.warn("GlobalExceptionHandler request parameter error: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(CommonResponse.error(400, e.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<CommonResponse<?>> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e) {
        log.warn("GlobalExceptionHandler MaxUploadSizeExceededException occurred: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(CommonResponse.error(413, "Payload Too Large: The uploaded file exceeds the size limit."));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<CommonResponse<?>> handleIllegalArgumentException(IllegalArgumentException e) {
        log.error("GlobalExceptionHandler IllegalArgumentException occurred: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(CommonResponse.error(400));
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
