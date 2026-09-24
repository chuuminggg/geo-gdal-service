package com.minju.geogdalservice.config;

import com.minju.geogdalservice.util.GdalInitializer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.gdal.gdal.gdal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.net.URI;

/**
 * GDAL 가상 파일시스템(/vsis3/) 설정.
 * COG 를 S3 에서 통째로 내려받지 않고 필요한 타일만 HTTP Range 요청으로 읽기 위해 사용한다.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class GdalS3Config {

    private final GdalInitializer gdalInitializer;

    @Value("${aws.access.key:}")
    private String accessKey;

    @Value("${aws.secret.key:}")
    private String secretKey;

    @Value("${aws.s3.region}")
    private String region;

    @Value("${aws.s3.endpoint:}")
    private String endpoint;

    @PostConstruct
    void configure() {
        if (!gdalInitializer.isAvailable()) {
            return;
        }
        gdal.SetConfigOption("AWS_REGION", region);
        if (!accessKey.isEmpty() && !secretKey.isEmpty()) {
            gdal.SetConfigOption("AWS_ACCESS_KEY_ID", accessKey);
            gdal.SetConfigOption("AWS_SECRET_ACCESS_KEY", secretKey);
        }
        if (!endpoint.isEmpty()) {
            // LocalStack 등 S3 호환 스토리지: host:port 형식 + path-style
            URI uri = URI.create(endpoint);
            gdal.SetConfigOption("AWS_S3_ENDPOINT", uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : ""));
            gdal.SetConfigOption("AWS_HTTPS", "https".equalsIgnoreCase(uri.getScheme()) ? "YES" : "NO");
            gdal.SetConfigOption("AWS_VIRTUAL_HOSTING", "FALSE");
        }
        // 파일을 열 때 디렉터리 목록 조회(LIST 요청)를 생략해 지연시간 감소
        gdal.SetConfigOption("GDAL_DISABLE_READDIR_ON_OPEN", "EMPTY_DIR");
        gdal.SetConfigOption("CPL_VSIL_CURL_ALLOWED_EXTENSIONS", ".tif,.tiff");
        // 스토리지 장애 시 조회 요청이 오래 붙잡히지 않도록 타임아웃 (초)
        gdal.SetConfigOption("GDAL_HTTP_CONNECTTIMEOUT", "5");
        gdal.SetConfigOption("GDAL_HTTP_TIMEOUT", "30");
        // 한 번 읽은 블록은 프로세스 메모리에 캐시
        gdal.SetConfigOption("VSI_CACHE", "TRUE");
        gdal.SetConfigOption("VSI_CACHE_SIZE", String.valueOf(64 * 1024 * 1024));
        log.info("GDAL /vsis3/ configured: region={}, endpoint={}", region, endpoint.isEmpty() ? "AWS" : endpoint);
    }

    public static String vsis3Path(String bucket, String key) {
        return "/vsis3/" + bucket + "/" + key;
    }
}
