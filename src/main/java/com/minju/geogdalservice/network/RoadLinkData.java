package com.minju.geogdalservice.network;

import org.locationtech.jts.geom.LineString;

/**
 * 토폴로지가 구성된 도로 링크. oneway 인 경우 geometry 방향(from -> to)으로만 통행 가능.
 */
public record RoadLinkData(
        String sourceId,
        int fromNode,
        int toNode,
        String roadClass,
        String name,
        boolean oneway,
        Integer maxSpeedKph,
        double lengthM,
        LineString geometry
) {
}
