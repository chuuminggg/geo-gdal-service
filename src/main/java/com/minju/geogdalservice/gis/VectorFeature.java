package com.minju.geogdalservice.gis;

import org.locationtech.jts.geom.Geometry;

import java.util.Map;

/**
 * 벡터 피처 (geometry 는 EPSG:4326, 속성 키는 소문자)
 */
public record VectorFeature(Geometry geometry, Map<String, String> attributes) {

    // 후보 속성명 중 처음으로 값이 있는 것을 반환 (데이터 출처마다 컬럼명이 다르므로)
    public String attr(String... candidates) {
        for (String key : candidates) {
            String value = attributes.get(key);
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
