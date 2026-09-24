package com.minju.geogdalservice.validation;

import com.minju.geogdalservice.entity.DatasetType;
import com.minju.geogdalservice.gis.RasterInfo;
import org.springframework.stereotype.Component;

/**
 * 래스터(GeoTIFF/DEM) 검수 규칙 모음
 */
public final class RasterRules {

    private RasterRules() {
    }

    abstract static class RasterRule implements ValidationRule<RasterInfo> {
        @Override
        public boolean supports(DatasetType type) {
            return type == DatasetType.RASTER;
        }
    }

    // 좌표계가 없으면 위치를 알 수 없으므로 불합격
    @Component
    public static class CrsRule extends RasterRule {
        public String code() {
            return "RASTER_CRS";
        }

        public RuleResult validate(RasterInfo info) {
            if (!info.hasCrs()) {
                return RuleResult.error(code(), "좌표계(CRS) 정보가 없습니다.");
            }
            return RuleResult.pass(code(), info.epsg() == null
                    ? "좌표계가 있으나 EPSG 코드로 식별되지 않습니다."
                    : "EPSG:" + info.epsg());
        }
    }

    @Component
    public static class GeoreferenceRule extends RasterRule {
        public String code() {
            return "RASTER_GEOREFERENCE";
        }

        public RuleResult validate(RasterInfo info) {
            if (!info.isGeoreferenced()) {
                return RuleResult.error(code(), "GeoTransform(지리 참조) 정보가 없습니다.");
            }
            return RuleResult.pass(code(), "GeoTransform 정보가 있습니다.");
        }
    }

    @Component
    public static class BoundsRule extends RasterRule {
        public String code() {
            return BoundsCheck.CODE;
        }

        public RuleResult validate(RasterInfo info) {
            return BoundsCheck.check(info.footprint());
        }
    }

    // NoData 비율: 거의 전부 NoData 면 빈 데이터로 보고 불합격
    @Component
    public static class NoDataRatioRule extends RasterRule {
        static final double EMPTY_THRESHOLD = 0.999;
        static final double WARN_THRESHOLD = 0.5;

        public String code() {
            return "RASTER_NODATA_RATIO";
        }

        public RuleResult validate(RasterInfo info) {
            Double ratio = info.noDataRatio();
            if (ratio == null) {
                return RuleResult.pass(code(), "NoData 비율을 계산하지 못해 검사를 생략합니다.");
            }
            String percent = "%.1f%%".formatted(ratio * 100);
            if (ratio >= EMPTY_THRESHOLD) {
                return RuleResult.error(code(), "유효한 픽셀이 없습니다. (NoData " + percent + ")");
            }
            if (ratio > WARN_THRESHOLD) {
                return RuleResult.warn(code(), "NoData 비율이 높습니다: " + percent);
            }
            return RuleResult.pass(code(), "NoData " + percent);
        }
    }

    // 해상도 이상치 (단위 오류로 잘못 저장된 데이터 탐지)
    @Component
    public static class ResolutionRule extends RasterRule {
        static final double MIN_M = 0.05;
        static final double MAX_M = 1000;

        public String code() {
            return "RASTER_RESOLUTION";
        }

        public RuleResult validate(RasterInfo info) {
            Double res = info.resolutionM();
            if (res == null) {
                return RuleResult.pass(code(), "해상도를 계산하지 못해 검사를 생략합니다.");
            }
            if (res < MIN_M || res > MAX_M) {
                return RuleResult.warn(code(), "해상도가 일반적인 범위(%.2fm ~ %.0fm)를 벗어났습니다: %.3fm"
                        .formatted(MIN_M, MAX_M, res));
            }
            return RuleResult.pass(code(), "해상도 %.3fm".formatted(res));
        }
    }
}
