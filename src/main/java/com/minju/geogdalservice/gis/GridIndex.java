package com.minju.geogdalservice.gis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 경위도 균일 격자 기반 인메모리 공간 인덱스 (불변, 스레드 안전).
 *
 * 점들을 cellDeg 크기의 격자 셀로 버킷팅해 두고,
 * - 최근접 검색: 질의 셀에서 링(ring)을 한 겹씩 넓혀가며 탐색하고, 다음 링까지의 최소 거리가
 *   현재 최선 거리보다 크면 조기 종료한다.
 * - 반경 검색: 반경을 덮는 셀들만 확인한다.
 *
 * 점이 고르게 분포한 도로 노드 같은 데이터에서 질의당 O(1)에 가까운 셀만 확인하므로
 * 전수 탐색 O(n) 대비 크게 빠르다. (GridIndexTest 의 비교 참고)
 */
public final class GridIndex {

    private static final double METERS_PER_DEGREE_LAT = 110_574;
    private static final double METERS_PER_DEGREE_LON_EQUATOR = 111_320;

    private final double cellDeg;
    private final double[] lons;
    private final double[] lats;
    private final Map<Long, int[]> cells;
    private final int minCx, maxCx, minCy, maxCy;

    private GridIndex(double cellDeg, double[] lons, double[] lats, Map<Long, int[]> cells,
                      int minCx, int maxCx, int minCy, int maxCy) {
        this.cellDeg = cellDeg;
        this.lons = lons;
        this.lats = lats;
        this.cells = cells;
        this.minCx = minCx;
        this.maxCx = maxCx;
        this.minCy = minCy;
        this.maxCy = maxCy;
    }

    /**
     * @param cellDeg 셀 크기(도). 0.002 ≈ 200m
     */
    public static GridIndex build(double[] lons, double[] lats, double cellDeg) {
        if (lons.length != lats.length) {
            throw new IllegalArgumentException("lons/lats 길이가 다릅니다.");
        }
        Map<Long, List<Integer>> buckets = new HashMap<>();
        int minCx = Integer.MAX_VALUE, maxCx = Integer.MIN_VALUE, minCy = Integer.MAX_VALUE, maxCy = Integer.MIN_VALUE;
        for (int i = 0; i < lons.length; i++) {
            int cx = cell(lons[i], cellDeg);
            int cy = cell(lats[i], cellDeg);
            buckets.computeIfAbsent(key(cx, cy), k -> new ArrayList<>()).add(i);
            minCx = Math.min(minCx, cx);
            maxCx = Math.max(maxCx, cx);
            minCy = Math.min(minCy, cy);
            maxCy = Math.max(maxCy, cy);
        }
        // List<Integer> -> int[] 로 압축 (박싱 제거, 캐시 지역성)
        Map<Long, int[]> cells = new HashMap<>(buckets.size() * 2);
        buckets.forEach((k, list) -> cells.put(k, list.stream().mapToInt(Integer::intValue).toArray()));
        return new GridIndex(cellDeg, lons.clone(), lats.clone(), cells, minCx, maxCx, minCy, maxCy);
    }

    public int size() {
        return lons.length;
    }

    /**
     * 가장 가까운 점의 인덱스 (maxDistanceM 안에 없으면 -1)
     */
    public int nearest(double lon, double lat, double maxDistanceM) {
        if (lons.length == 0) {
            return -1;
        }
        int qx = cell(lon, cellDeg);
        int qy = cell(lat, cellDeg);
        // 질의점에서 격자 전체를 덮는 데 필요한 최대 링
        int maxRing = Math.max(Math.max(Math.abs(qx - minCx), Math.abs(qx - maxCx)),
                Math.max(Math.abs(qy - minCy), Math.abs(qy - maxCy)));
        double cellMinM = cellMinMeters(lat);

        int best = -1;
        double bestDist = maxDistanceM;
        for (int ring = 0; ring <= maxRing; ring++) {
            // 링 r 의 셀은 질의점으로부터 최소 (r-1)칸 떨어져 있다
            double ringLowerBound = Math.max(0, ring - 1) * cellMinM;
            if (ringLowerBound > bestDist) {
                break;
            }
            for (int cx = qx - ring; cx <= qx + ring; cx++) {
                for (int cy = qy - ring; cy <= qy + ring; cy++) {
                    // 링의 테두리 셀만 (안쪽은 이전 링에서 확인함)
                    if (Math.abs(cx - qx) != ring && Math.abs(cy - qy) != ring) {
                        continue;
                    }
                    int[] ids = cells.get(key(cx, cy));
                    if (ids == null) {
                        continue;
                    }
                    for (int id : ids) {
                        double d = GeometryUtils.haversine(lon, lat, lons[id], lats[id]);
                        if (d <= bestDist) {
                            bestDist = d;
                            best = id;
                        }
                    }
                }
            }
        }
        return best;
    }

    /**
     * 반경(m) 안의 모든 점 인덱스
     */
    public List<Integer> withinRadius(double lon, double lat, double radiusM) {
        double dLat = radiusM / METERS_PER_DEGREE_LAT;
        double dLon = radiusM / (METERS_PER_DEGREE_LON_EQUATOR * Math.max(0.01, Math.cos(Math.toRadians(Math.abs(lat) + dLat))));
        int x0 = cell(lon - dLon, cellDeg), x1 = cell(lon + dLon, cellDeg);
        int y0 = cell(lat - dLat, cellDeg), y1 = cell(lat + dLat, cellDeg);

        List<Integer> result = new ArrayList<>();
        for (int cx = x0; cx <= x1; cx++) {
            for (int cy = y0; cy <= y1; cy++) {
                int[] ids = cells.get(key(cx, cy));
                if (ids == null) {
                    continue;
                }
                for (int id : ids) {
                    if (GeometryUtils.haversine(lon, lat, lons[id], lats[id]) <= radiusM) {
                        result.add(id);
                    }
                }
            }
        }
        return result;
    }

    // 셀 한 칸의 최소 폭(m). 경도 방향은 고위도일수록 좁아지므로 보수적으로 계산
    private double cellMinMeters(double lat) {
        double lonMeters = cellDeg * METERS_PER_DEGREE_LON_EQUATOR * Math.cos(Math.toRadians(Math.min(89, Math.abs(lat) + 1)));
        return Math.min(lonMeters, cellDeg * METERS_PER_DEGREE_LAT);
    }

    private static int cell(double v, double cellDeg) {
        return (int) Math.floor(v / cellDeg);
    }

    private static long key(int cx, int cy) {
        return ((long) cx << 32) | (cy & 0xffffffffL);
    }
}
