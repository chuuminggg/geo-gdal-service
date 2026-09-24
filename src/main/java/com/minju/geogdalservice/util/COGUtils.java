package com.minju.geogdalservice.util;

import lombok.RequiredArgsConstructor;
import org.gdal.gdal.Dataset;
import org.gdal.gdal.TranslateOptions;
import org.gdal.gdal.gdal;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Vector;

@Component
@RequiredArgsConstructor
public class COGUtils {

    private final GdalInitializer gdalInitializer;

    // COG 변환 로직 (파일 → 파일, 전체 데이터를 JVM 메모리에 올리지 않음)
    public void transformToCOG(Path source, Path target) {
        gdalInitializer.requireAvailable();

        Dataset inputDataset = null;
        Dataset translated = null;
        try {
            inputDataset = gdal.Open(source.toString());
            if (inputDataset == null) {
                throw new IllegalArgumentException("GDAL로 열 수 없는 파일입니다: " + gdal.GetLastErrorMsg());
            }

            // GDAL Translate로 COG 변환 (COG 드라이버가 타일링 + 오버뷰를 자동 생성)
            Vector<String> options = new Vector<>();
            options.add("-of");
            options.add("COG");
            options.add("-co");
            options.add("COMPRESS=DEFLATE");
            options.add("-co");
            options.add("BLOCKSIZE=512");
            options.add("-co");
            options.add("OVERVIEWS=AUTO");

            translated = gdal.Translate(target.toString(), inputDataset, new TranslateOptions(options));
            if (translated == null) {
                throw new IllegalStateException("COG 변환 실패: " + gdal.GetLastErrorMsg());
            }
        } finally {
            // 예외가 발생해도 네이티브 리소스 해제
            if (translated != null) translated.delete();
            if (inputDataset != null) inputDataset.delete();
        }
    }
}
