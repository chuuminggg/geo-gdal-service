package com.minju.geogdalservice.validation;

import com.minju.geogdalservice.entity.DatasetType;

/**
 * 검수 규칙. 새 규칙은 이 인터페이스를 구현한 @Component 를 추가하기만 하면
 * ValidationService 가 자동으로 수집해서 실행한다. (OCP)
 *
 * @param <T> 검수 대상 (RasterInfo, VectorData)
 */
public interface ValidationRule<T> {

    String code();

    boolean supports(DatasetType type);

    RuleResult validate(T target);
}
