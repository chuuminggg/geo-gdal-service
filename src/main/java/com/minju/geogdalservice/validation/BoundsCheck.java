package com.minju.geogdalservice.validation;

import com.minju.geogdalservice.gis.GeometryUtils;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;

// 데이터 범위가 서비스 대상 영역(대한민국) 안에 있는지 검사
final class BoundsCheck {

    static final String CODE = "WITHIN_SERVICE_AREA";

    private BoundsCheck() {
    }

    static RuleResult check(Geometry footprint) {
        if (footprint == null || footprint.isEmpty()) {
            return RuleResult.pass(CODE, "범위를 계산할 수 없어 검사를 생략합니다.");
        }
        Envelope env = footprint.getEnvelopeInternal();
        Envelope korea = GeometryUtils.KOREA_BOUNDS;
        if (!korea.intersects(env)) {
            return RuleResult.error(CODE, "데이터 범위가 서비스 영역(대한민국) 밖에 있습니다. 좌표계 설정을 확인하세요: " + env);
        }
        if (!korea.contains(env)) {
            return RuleResult.warn(CODE, "데이터 일부가 서비스 영역(대한민국) 밖에 있습니다: " + env);
        }
        return RuleResult.pass(CODE, "데이터 범위가 서비스 영역 안에 있습니다.");
    }
}
