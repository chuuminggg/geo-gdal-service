package com.minju.geogdalservice.gis;

import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Polygon;

import java.util.List;

/**
 * GDAL/OGR 로 읽은 벡터 레이어. GDAL 객체와 분리된 값 객체.
 */
public record VectorData(List<VectorFeature> features, Integer sourceEpsg) {

    public Envelope bounds() {
        Envelope env = new Envelope();
        for (VectorFeature f : features) {
            if (f.geometry() != null && !f.geometry().isEmpty()) {
                env.expandToInclude(f.geometry().getEnvelopeInternal());
            }
        }
        return env;
    }

    // 데이터 범위 폴리곤 (포인트 하나뿐인 경우처럼 면적이 0이면 약간 확장)
    public Polygon footprint() {
        Envelope env = bounds();
        if (env.isNull()) {
            return null;
        }
        if (env.getWidth() == 0 || env.getHeight() == 0) {
            env.expandBy(1e-6);
        }
        return (Polygon) GeometryUtils.FACTORY.toGeometry(env);
    }
}
