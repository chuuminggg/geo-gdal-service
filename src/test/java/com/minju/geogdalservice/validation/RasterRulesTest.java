package com.minju.geogdalservice.validation;

import com.minju.geogdalservice.gis.GeometryUtils;
import com.minju.geogdalservice.gis.RasterInfo;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RasterRulesTest {

    private final RasterInfo valid = RasterInfo.builder()
            .width(100).height(100).bandCount(1).dataType("Float32")
            .epsg(5186).srsWkt("PROJCS[...]")
            .geoTransform(new double[]{200000, 30, 0, 550000, 0, -30})
            .resolutionM(30.0).noDataRatio(0.01)
            .footprint(GeometryUtils.envelope(127.0, 37.5, 127.03, 37.53))
            .build();

    @Test
    void 정상_래스터는_모든_규칙을_통과한다() {
        assertThat(new RasterRules.CrsRule().validate(valid).passed()).isTrue();
        assertThat(new RasterRules.GeoreferenceRule().validate(valid).passed()).isTrue();
        assertThat(new RasterRules.BoundsRule().validate(valid).passed()).isTrue();
        assertThat(new RasterRules.NoDataRatioRule().validate(valid).passed()).isTrue();
        assertThat(new RasterRules.ResolutionRule().validate(valid).passed()).isTrue();
    }

    @Test
    void 좌표계와_지리참조가_없으면_불합격() {
        RasterInfo noCrs = RasterInfo.builder().width(10).height(10).bandCount(1).dataType("Byte")
                .geoTransform(new double[]{0, 1, 0, 0, 0, 1}).build();

        assertThat(new RasterRules.CrsRule().validate(noCrs).severity()).isEqualTo(Severity.ERROR);
        assertThat(new RasterRules.GeoreferenceRule().validate(noCrs).severity()).isEqualTo(Severity.ERROR);
    }

    @Test
    void 서비스_영역_밖이면_불합격_일부만_걸치면_경고() {
        RasterInfo tokyo = RasterInfo.builder().footprint(GeometryUtils.envelope(139.6, 35.6, 139.8, 35.8)).build();
        RasterInfo border = RasterInfo.builder().footprint(GeometryUtils.envelope(131.5, 37.0, 132.5, 37.5)).build();

        assertThat(new RasterRules.BoundsRule().validate(tokyo).severity()).isEqualTo(Severity.ERROR);
        assertThat(new RasterRules.BoundsRule().validate(border).severity()).isEqualTo(Severity.WARN);
    }

    @Test
    void NoData_비율에_따라_경고_또는_불합격() {
        RasterRules.NoDataRatioRule rule = new RasterRules.NoDataRatioRule();
        assertThat(rule.validate(RasterInfo.builder().noDataRatio(0.7).build()).severity()).isEqualTo(Severity.WARN);
        assertThat(rule.validate(RasterInfo.builder().noDataRatio(1.0).build()).severity()).isEqualTo(Severity.ERROR);
    }

    @Test
    void 해상도_이상치는_경고() {
        RasterRules.ResolutionRule rule = new RasterRules.ResolutionRule();
        assertThat(rule.validate(RasterInfo.builder().resolutionM(5000.0).build()).severity()).isEqualTo(Severity.WARN);
        assertThat(rule.validate(RasterInfo.builder().resolutionM(0.01).build()).severity()).isEqualTo(Severity.WARN);
    }
}
