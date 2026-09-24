package com.minju.geogdalservice.validation;

import com.minju.geogdalservice.entity.DatasetType;
import com.minju.geogdalservice.gis.RasterInfo;
import com.minju.geogdalservice.gis.VectorData;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 등록된 검수 규칙(@Component)을 데이터 타입별로 모아 실행한다.
 */
@Service
@RequiredArgsConstructor
public class ValidationService {

    private final List<ValidationRule<RasterInfo>> rasterRules;
    private final List<ValidationRule<VectorData>> vectorRules;

    public List<RuleResult> validateRaster(RasterInfo info) {
        return run(rasterRules, info, DatasetType.RASTER);
    }

    public List<RuleResult> validateVector(VectorData data, DatasetType type) {
        return run(vectorRules, data, type);
    }

    private <T> List<RuleResult> run(List<ValidationRule<T>> rules, T target, DatasetType type) {
        return rules.stream()
                .filter(rule -> rule.supports(type))
                .map(rule -> rule.validate(target))
                .toList();
    }

    public static boolean hasBlockingError(List<RuleResult> results) {
        return results.stream().anyMatch(RuleResult::isBlocking);
    }
}
