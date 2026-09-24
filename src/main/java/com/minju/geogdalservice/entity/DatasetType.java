package com.minju.geogdalservice.entity;

public enum DatasetType {
    RASTER,        // GeoTIFF / DEM 등 래스터
    ROAD_NETWORK,  // 경로탐색용 도로 링크(LineString)
    POI            // 관심지점(Point)
}
