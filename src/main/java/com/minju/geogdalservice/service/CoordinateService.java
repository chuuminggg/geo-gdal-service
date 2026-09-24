package com.minju.geogdalservice.service;

import com.minju.geogdalservice.gis.GdalSrs;
import com.minju.geogdalservice.util.GdalInitializer;
import lombok.RequiredArgsConstructor;
import org.gdal.osr.CoordinateTransformation;
import org.gdal.osr.SpatialReference;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * GDAL/PROJ 기반 좌표계 변환 (예: EPSG:4326 <-> EPSG:5179 UTM-K, EPSG:5186 중부원점)
 */
@Service
@RequiredArgsConstructor
public class CoordinateService {

    public static final int MAX_POINTS = 10_000;

    private final GdalInitializer gdalInitializer;

    public List<double[]> transform(List<double[]> coordinates, int fromEpsg, int toEpsg) {
        gdalInitializer.requireAvailable();
        if (coordinates.size() > MAX_POINTS) {
            throw new IllegalArgumentException("한 번에 변환할 수 있는 좌표는 최대 %d개입니다.".formatted(MAX_POINTS));
        }

        SpatialReference from = GdalSrs.fromEpsg(fromEpsg);
        SpatialReference to;
        try {
            to = GdalSrs.fromEpsg(toEpsg);
        } catch (IllegalArgumentException e) {
            from.delete();
            throw e;
        }
        CoordinateTransformation ct;
        try {
            ct = CoordinateTransformation.CreateCoordinateTransformation(from, to);
        } catch (RuntimeException e) {
            ct = null;
        }
        if (ct == null) {
            from.delete();
            to.delete();
            throw new IllegalArgumentException("EPSG:%d -> EPSG:%d 변환을 지원하지 않습니다.".formatted(fromEpsg, toEpsg));
        }
        try {
            // 변환 객체 생성 비용이 크므로 여러 좌표를 한 번에 변환
            List<double[]> result = new ArrayList<>(coordinates.size());
            double[] out = new double[3];
            for (double[] c : coordinates) {
                if (c == null || c.length < 2) {
                    throw new IllegalArgumentException("좌표는 [x, y] 형식이어야 합니다.");
                }
                try {
                    ct.TransformPoint(out, c[0], c[1]);
                } catch (RuntimeException e) {
                    out[0] = Double.NaN;
                }
                if (!Double.isFinite(out[0]) || !Double.isFinite(out[1])) {
                    throw new IllegalArgumentException("변환할 수 없는 좌표입니다: [%s, %s]".formatted(c[0], c[1]));
                }
                result.add(new double[]{out[0], out[1]});
            }
            return result;
        } finally {
            ct.delete();
            from.delete();
            to.delete();
        }
    }
}
