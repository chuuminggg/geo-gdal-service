package com.minju.geogdalservice.pipeline;

import com.minju.geogdalservice.gis.RasterInfo;
import com.minju.geogdalservice.validation.RuleResult;
import org.locationtech.jts.geom.Polygon;

import java.util.List;

/**
 * @param rasterInfo 래스터인 경우에만 존재 (raster_metadata 저장용)
 */
public record ValidationOutcome(List<RuleResult> results, Polygon footprint, Integer featureCount,
                                RasterInfo rasterInfo) {

    // 파일 자체를 읽을 수 없는 경우 (포맷 오류) -> 불합격
    public static ValidationOutcome unreadable(String message) {
        return new ValidationOutcome(List.of(RuleResult.error("READABLE", message)), null, null, null);
    }
}
