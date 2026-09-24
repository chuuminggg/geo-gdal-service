package com.minju.geogdalservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3ServiceImpl implements S3Service {

    private final S3Client s3Client;

    @Override
    public void upload(String bucket, String key, Path file, String contentType) {
        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(contentType)
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromFile(file));

            log.info("Successfully uploaded file to S3: bucket={}, key={}", bucket, key);

        } catch (SdkException e) {
            log.error("Failed to upload file to S3: bucket={}, key={}", bucket, key, e);
            throw new IllegalStateException("S3 업로드 실패: " + key, e);
        }
    }

    @Override
    public void download(String bucket, String key, Path target) {
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();

            // SDK가 target 파일을 직접 생성하므로 기존 파일(임시 파일 등)은 먼저 제거
            Files.deleteIfExists(target);
            s3Client.getObject(getObjectRequest, ResponseTransformer.toFile(target));

            log.info("Successfully downloaded file from S3: bucket={}, key={}", bucket, key);

        } catch (IOException | SdkException e) {
            log.error("Failed to download file from S3: bucket={}, key={}", bucket, key, e);
            throw new IllegalStateException("S3 다운로드 실패: " + key, e);
        }
    }

    @Override
    public boolean exists(String bucket, String key) {
        try {
            HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();

            s3Client.headObject(headObjectRequest);
            return true;

        } catch (NoSuchKeyException e) {
            return false;
        } catch (Exception e) {
            log.error("Failed to check file existence in S3: bucket={}, key={}", bucket, key, e);
            return false;
        }
    }
}
