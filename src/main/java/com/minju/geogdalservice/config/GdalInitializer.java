package com.minju.geogdalservice.config;

import com.minju.geogdalservice.common.exception.GeoServiceException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.gdal.gdal.gdal;
import org.gdal.osr.osr;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

// GDAL 네이티브 라이브러리 초기화
// 네이티브 라이브러리(gdalalljni)가 없어도 애플리케이션은 기동되고, GDAL 기능 호출 시 503을 반환한다.
@Slf4j
@Component
public class GdalInitializer {

    private volatile boolean available;
    private volatile String unavailableReason = "GDAL is not initialized";

    @PostConstruct
    public void init() {
        try {
            gdal.AllRegister();
            gdal.UseExceptions();
            osr.UseExceptions();
            available = true;
            log.info("GDAL initialized: {}", gdal.VersionInfo("--version"));
        } catch (LinkageError e) {
            // 최초 로딩 실패는 UnsatisfiedLinkError, 이후 재시도는 NoClassDefFoundError
            unavailableReason = e.getMessage();
            log.warn("GDAL native library could not be loaded. GDAL APIs are disabled: {}", e.getMessage());
        }
    }

    public boolean isAvailable() {
        return available;
    }

    public void ensureAvailable() {
        if (!available) {
            throw new GeoServiceException(HttpStatus.SERVICE_UNAVAILABLE,
                    "GDAL native library is not available: " + unavailableReason);
        }
    }
}
