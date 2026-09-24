package com.minju.geogdalservice.util;

import com.minju.geogdalservice.common.exception.GdalUnavailableException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.gdal.gdal.gdal;
import org.gdal.ogr.ogr;
import org.springframework.stereotype.Component;

/**
 * GDAL 네이티브 라이브러리 로딩을 한 곳에서 담당.
 * static 블록에서 바로 AllRegister()를 호출하면 네이티브 라이브러리가 없는 환경에서
 * UnsatisfiedLinkError로 애플리케이션 전체가 뜨지 않으므로, 실패를 기록만 하고
 * GDAL이 필요한 기능 호출 시점에 명확한 예외를 던진다.
 */
@Slf4j
@Component
public class GdalInitializer {

    private volatile boolean available;
    private volatile String version;

    @PostConstruct
    public void init() {
        try {
            gdal.AllRegister();
            ogr.RegisterAll();
            version = gdal.VersionInfo("RELEASE_NAME");
            available = true;
            log.info("GDAL loaded: version={}", version);
        } catch (UnsatisfiedLinkError | NoClassDefFoundError e) {
            available = false;
            log.warn("GDAL native library could not be loaded. GDAL features are disabled: {}", e.getMessage());
        }
    }

    public boolean isAvailable() {
        return available;
    }

    public String getVersion() {
        return version;
    }

    public void requireAvailable() {
        if (!available) {
            throw new GdalUnavailableException("GDAL 네이티브 라이브러리가 로드되지 않았습니다. GDAL 설치 및 java.library.path를 확인해주세요.");
        }
    }
}
