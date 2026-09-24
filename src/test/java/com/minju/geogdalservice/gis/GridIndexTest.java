package com.minju.geogdalservice.gis;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class GridIndexTest {

    // 서울 일대 약 20km x 20km 에 무작위 점
    private static final double MIN_LON = 126.9, MIN_LAT = 37.45, SPAN = 0.2;

    @Test
    void 최근접_검색은_전수_탐색과_같은_결과를_낸다() {
        Random random = new Random(42);
        double[][] pts = randomPoints(random, 20_000);
        GridIndex index = GridIndex.build(pts[0], pts[1], 0.002);

        for (int q = 0; q < 2_000; q++) {
            // 데이터 범위 밖 질의도 포함
            double lon = MIN_LON - 0.02 + random.nextDouble() * (SPAN + 0.04);
            double lat = MIN_LAT - 0.02 + random.nextDouble() * (SPAN + 0.04);

            int expected = bruteForceNearest(pts, lon, lat);
            int actual = index.nearest(lon, lat, Double.MAX_VALUE);

            assertThat(GeometryUtils.haversine(lon, lat, pts[0][actual], pts[1][actual]))
                    .isEqualTo(GeometryUtils.haversine(lon, lat, pts[0][expected], pts[1][expected]));
        }
    }

    @Test
    void 최대거리_밖이면_찾지_않는다() {
        GridIndex index = GridIndex.build(new double[]{127.0}, new double[]{37.5}, 0.002);

        assertThat(index.nearest(127.0, 37.501, 200)).isEqualTo(0);      // 약 111m
        assertThat(index.nearest(127.0, 37.51, 200)).isEqualTo(-1);      // 약 1.1km
        assertThat(GridIndex.build(new double[0], new double[0], 0.002).nearest(127, 37.5, 1000)).isEqualTo(-1);
    }

    @Test
    void 반경_검색은_전수_탐색과_같은_결과를_낸다() {
        Random random = new Random(7);
        double[][] pts = randomPoints(random, 10_000);
        GridIndex index = GridIndex.build(pts[0], pts[1], 0.002);

        for (int q = 0; q < 200; q++) {
            double lon = MIN_LON + random.nextDouble() * SPAN;
            double lat = MIN_LAT + random.nextDouble() * SPAN;
            double radius = 100 + random.nextDouble() * 1500;

            List<Integer> expected = new ArrayList<>();
            for (int i = 0; i < pts[0].length; i++) {
                if (GeometryUtils.haversine(lon, lat, pts[0][i], pts[1][i]) <= radius) {
                    expected.add(i);
                }
            }
            assertThat(index.withinRadius(lon, lat, radius)).containsExactlyInAnyOrderElementsOf(expected);
        }
    }

    @Test
    void 격자_인덱스가_전수_탐색보다_빠르다() {
        Random random = new Random(1);
        double[][] pts = randomPoints(random, 100_000);
        GridIndex index = GridIndex.build(pts[0], pts[1], 0.002);
        double[][] queries = randomPoints(random, 1_000);

        // JIT 워밍업
        for (int i = 0; i < 200; i++) {
            index.nearest(queries[0][i], queries[1][i], Double.MAX_VALUE);
            bruteForceNearest(pts, queries[0][i], queries[1][i]);
        }

        long t0 = System.nanoTime();
        for (int i = 0; i < queries[0].length; i++) {
            index.nearest(queries[0][i], queries[1][i], Double.MAX_VALUE);
        }
        long gridNanos = System.nanoTime() - t0;

        t0 = System.nanoTime();
        for (int i = 0; i < queries[0].length; i++) {
            bruteForceNearest(pts, queries[0][i], queries[1][i]);
        }
        long bruteNanos = System.nanoTime() - t0;

        System.out.printf("[GridIndex] 100,000 points / 1,000 queries: grid=%.1fms, brute-force=%.1fms (x%.0f)%n",
                gridNanos / 1e6, bruteNanos / 1e6, (double) bruteNanos / gridNanos);
        assertThat(gridNanos).isLessThan(bruteNanos);
    }

    private double[][] randomPoints(Random random, int n) {
        double[] lons = new double[n];
        double[] lats = new double[n];
        for (int i = 0; i < n; i++) {
            lons[i] = MIN_LON + random.nextDouble() * SPAN;
            lats[i] = MIN_LAT + random.nextDouble() * SPAN;
        }
        return new double[][]{lons, lats};
    }

    private int bruteForceNearest(double[][] pts, double lon, double lat) {
        int best = -1;
        double bestDist = Double.MAX_VALUE;
        for (int i = 0; i < pts[0].length; i++) {
            double d = GeometryUtils.haversine(lon, lat, pts[0][i], pts[1][i]);
            if (d < bestDist) {
                bestDist = d;
                best = i;
            }
        }
        return best;
    }
}
