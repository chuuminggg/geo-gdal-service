package com.minju.geogdalservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

import java.net.URI;
import java.time.Duration;

@Configuration
public class S3Config {

    @Value("${aws.access.key:}")
    private String accessKey;

    @Value("${aws.secret.key:}")
    private String secretKey;

    @Value("${aws.s3.region}")
    private String region;

    // LocalStack 등 S3 호환 스토리지 주소 (비어 있으면 AWS 기본 엔드포인트)
    @Value("${aws.s3.endpoint:}")
    private String endpoint;

    // 스토리지 장애 시 요청 스레드가 무한정 대기하지 않도록 타임아웃 설정 (대용량 업로드를 고려해 넉넉하게)
    @Value("${aws.s3.api-call-timeout:120s}")
    private Duration apiCallTimeout;

    @Value("${aws.s3.api-call-attempt-timeout:60s}")
    private Duration apiCallAttemptTimeout;

    @Bean
    public S3Client s3Client() {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(region))
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallTimeout(apiCallTimeout)
                        .apiCallAttemptTimeout(apiCallAttemptTimeout)
                        .build());

        // AWS 자격 증명이 설정되지 않은 경우 기본 자격 증명 체인 사용
        if (!accessKey.isEmpty() && !secretKey.isEmpty()) {
            AwsBasicCredentials awsCredentials = AwsBasicCredentials.create(accessKey, secretKey);
            builder.credentialsProvider(StaticCredentialsProvider.create(awsCredentials));
        }

        if (!endpoint.isEmpty()) {
            builder.endpointOverride(URI.create(endpoint))
                    .forcePathStyle(true);
        }

        return builder.build();
    }
}
