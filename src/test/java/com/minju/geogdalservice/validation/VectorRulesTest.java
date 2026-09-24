package com.minju.geogdalservice.validation;

import com.minju.geogdalservice.gis.GeometryUtils;
import com.minju.geogdalservice.gis.VectorData;
import com.minju.geogdalservice.gis.VectorFeature;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Polygon;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.minju.geogdalservice.support.TestFeatures.line;
import static com.minju.geogdalservice.support.TestFeatures.point;
import static org.assertj.core.api.Assertions.assertThat;

class VectorRulesTest {

    @Test
    void 도로_데이터에_포인트만_있으면_불합격_일부면_경고() {
        var rule = new VectorRules.RoadGeometryTypeRule();

        assertThat(rule.validate(data(point("a", 127, 37.5))).severity()).isEqualTo(Severity.ERROR);
        assertThat(rule.validate(data(line(127, 37.5, 127.001, 37.5), point("a", 127, 37.5))).severity())
                .isEqualTo(Severity.WARN);
        assertThat(rule.validate(data()).severity()).isEqualTo(Severity.ERROR);
    }

    @Test
    void 자기교차_폴리곤_같은_무효_geometry_비율이_1퍼센트를_넘으면_불합격() {
        // 나비넥타이(자기교차) 폴리곤
        Polygon bowtie = GeometryUtils.FACTORY.createPolygon(new Coordinate[]{
                new Coordinate(127, 37), new Coordinate(127.01, 37.01), new Coordinate(127.01, 37),
                new Coordinate(127, 37.01), new Coordinate(127, 37)});
        VectorFeature invalid = new VectorFeature(bowtie, Map.of());
        var rule = new VectorRules.GeometryValidityRule();

        List<VectorFeature> mostlyValid = new ArrayList<>();
        for (int i = 0; i < 199; i++) {
            mostlyValid.add(point("p" + i, 127, 37.5));
        }
        mostlyValid.add(invalid);

        assertThat(rule.validate(data(invalid, point("a", 127, 37.5))).severity()).isEqualTo(Severity.ERROR);
        assertThat(rule.validate(new VectorData(mostlyValid, 4326)).severity()).isEqualTo(Severity.WARN);
    }

    @Test
    void 끊어진_도로망이_많으면_경고() {
        var rule = new VectorRules.RoadConnectivityRule();

        RuleResult connected = rule.validate(data(
                line(127.000, 37.5, 127.001, 37.5),
                line(127.001, 37.5, 127.002, 37.5)));
        RuleResult islands = rule.validate(data(
                line(127.000, 37.5, 127.001, 37.5),
                line(127.100, 37.5, 127.101, 37.5)));

        assertThat(connected.passed()).isTrue();
        assertThat(islands.severity()).isEqualTo(Severity.WARN);
        assertThat(islands.message()).contains("연결 요소 2개");
    }

    @Test
    void 제한속도가_없는_링크가_많으면_경고() {
        var rule = new VectorRules.RoadAttributeRule();
        RuleResult result = rule.validate(data(
                line(Map.of("highway", "primary", "maxspeed", "60"), 127.000, 37.5, 127.001, 37.5),
                line(Map.of("highway", "primary"), 127.001, 37.5, 127.002, 37.5),
                line(Map.of("highway", "primary"), 127.002, 37.5, 127.003, 37.5)));
        assertThat(result.severity()).isEqualTo(Severity.WARN);
    }

    @Test
    void 이름없는_POI가_절반을_넘으면_불합격() {
        var rule = new VectorRules.PoiNameRule();
        assertThat(rule.validate(data(point(null, 127, 37.5), point(null, 127, 37.5), point("a", 127, 37.5)))
                .severity()).isEqualTo(Severity.ERROR);
        assertThat(rule.validate(data(point(null, 127, 37.5), point("a", 127, 37.5), point("b", 127, 37.5)))
                .severity()).isEqualTo(Severity.WARN);
    }

    @Test
    void 벡터_범위_검사() {
        var rule = new VectorRules.BoundsRule();
        assertThat(rule.validate(data(point("서울", 126.97, 37.56))).passed()).isTrue();
        assertThat(rule.validate(data(point("도쿄", 139.7, 35.7))).severity()).isEqualTo(Severity.ERROR);
    }

    private VectorData data(VectorFeature... features) {
        return new VectorData(List.of(features), 4326);
    }
}
