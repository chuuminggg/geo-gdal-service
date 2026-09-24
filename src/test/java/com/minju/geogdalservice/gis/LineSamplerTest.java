package com.minju.geogdalservice.gis;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class LineSamplerTest {

    @Test
    void 일정_간격으로_샘플링하고_시작점과_끝점을_포함한다() {
        // 위도 0.01도 ≈ 1112m 직선
        List<LineSampler.Sample> samples = LineSampler.sample(
                List.of(new double[]{127.0, 37.0}, new double[]{127.0, 37.01}), 100, 1000);

        assertThat(samples).hasSize(13);   // 0, 100, ..., 1100, 1112
        assertThat(samples.get(0).distanceM()).isZero();
        assertThat(samples.get(1).distanceM()).isEqualTo(100);
        assertThat(samples.get(1).lat()).isCloseTo(37.0 + 0.01 * 100 / samples.get(12).distanceM(), within(1e-9));
        assertThat(samples.get(12).distanceM()).isCloseTo(1112, within(2.0));
        assertThat(samples.get(12).lat()).isEqualTo(37.01);
    }

    @Test
    void 여러_구간에_걸쳐_누적거리로_샘플링하고_정점을_포함한다() {
        // 북쪽으로 약 111m 갔다가 되돌아오는 경로 (꺾이는 정점 37.001)
        List<LineSampler.Sample> samples = LineSampler.sample(List.of(
                new double[]{127.0, 37.0}, new double[]{127.0, 37.001}, new double[]{127.0, 37.0}), 50, 1000);

        double half = GeometryUtils.haversine(127.0, 37.0, 127.0, 37.001);
        assertThat(samples).extracting(LineSampler.Sample::distanceM)
                .containsExactly(0.0, 50.0, 100.0, half, 150.0, 200.0, 2 * half);
        assertThat(samples.get(3).lat()).isEqualTo(37.001);
        assertThat(samples.get(4).lat()).isLessThan(37.001);
    }

    @Test
    void 잘못된_입력은_예외() {
        assertThatThrownBy(() -> LineSampler.sample(List.<double[]>of(new double[]{127, 37}), 10, 100))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LineSampler.sample(
                List.of(new double[]{127, 37}, new double[]{127, 38}), 1, 100))
                .isInstanceOf(IllegalArgumentException.class);   // 샘플 수 초과
    }
}
