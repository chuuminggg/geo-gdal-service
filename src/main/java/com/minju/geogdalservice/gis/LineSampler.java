package com.minju.geogdalservice.gis;

import java.util.ArrayList;
import java.util.List;

/**
 * 폴리라인을 일정 거리(m) 간격으로 샘플링 (고도 프로파일용).
 * 꺾이는 지점(정점)도 항상 포함해서 방향이 바뀌는 곳의 고도를 놓치지 않는다.
 */
public final class LineSampler {

    private LineSampler() {
    }

    public record Sample(double distanceM, double lon, double lat) {
    }

    /**
     * @param coordinates [[lon, lat], ...] (2개 이상)
     * @param intervalM   샘플 간격 (m). 시작점, 끝점, 정점은 항상 포함된다.
     */
    public static List<Sample> sample(List<double[]> coordinates, double intervalM, int maxSamples) {
        if (coordinates == null || coordinates.size() < 2) {
            throw new IllegalArgumentException("좌표가 2개 이상 필요합니다.");
        }
        if (intervalM <= 0) {
            throw new IllegalArgumentException("샘플 간격은 0보다 커야 합니다.");
        }
        List<Sample> samples = new ArrayList<>();
        double[] first = coordinates.get(0);
        samples.add(new Sample(0, first[0], first[1]));

        double travelled = 0;          // 현재 구간 시작점까지의 누적 거리
        double nextSampleAt = intervalM;
        for (int i = 1; i < coordinates.size(); i++) {
            double[] a = coordinates.get(i - 1);
            double[] b = coordinates.get(i);
            double segment = GeometryUtils.haversine(a[0], a[1], b[0], b[1]);
            // 이 구간 안에 들어가는 샘플 지점들을 선형 보간
            while (segment > 0 && nextSampleAt < travelled + segment) {
                if (samples.size() >= maxSamples) {
                    throw new IllegalArgumentException("샘플 수가 최대치(%d)를 초과합니다. 간격을 늘려주세요.".formatted(maxSamples));
                }
                double t = (nextSampleAt - travelled) / segment;
                samples.add(new Sample(nextSampleAt, a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t));
                nextSampleAt += intervalM;
            }
            travelled += segment;
            // 정점 (간격 샘플과 겹치면 중복 추가하지 않음)
            if (samples.get(samples.size() - 1).distanceM() < travelled) {
                samples.add(new Sample(travelled, b[0], b[1]));
            }
        }
        if (samples.size() > maxSamples) {
            throw new IllegalArgumentException("샘플 수가 최대치(%d)를 초과합니다. 간격을 늘려주세요.".formatted(maxSamples));
        }
        return samples;
    }
}
