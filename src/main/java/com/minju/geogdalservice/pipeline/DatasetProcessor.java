package com.minju.geogdalservice.pipeline;

import com.minju.geogdalservice.entity.DatasetType;
import com.minju.geogdalservice.util.TempWorkspace;

import java.nio.file.Path;
import java.util.Set;

/**
 * 데이터 타입별 검수/가공 전략.
 * 새 데이터 타입(예: 대중교통 정류장)은 구현체를 추가하면 파이프라인 코드 변경 없이 처리된다.
 */
public interface DatasetProcessor {

    DatasetType type();

    // 업로드 허용 확장자
    Set<String> allowedExtensions();

    ValidationOutcome validate(Path rawFile);

    /**
     * 가공 후 서비스용 데이터 생성.
     *
     * @return S3 가공 결과 키 (DB 적재만 하는 경우 null)
     */
    String process(JobContext context, Path rawFile, TempWorkspace workspace);
}
