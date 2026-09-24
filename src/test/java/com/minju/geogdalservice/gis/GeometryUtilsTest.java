package com.minju.geogdalservice.gis;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class GeometryUtilsTest {

    @Test
    void 서울시청에서_강남역까지_하버사인_거리() {
        // 서울시청(126.9780, 37.5665) - 강남역(127.0276, 37.4979): 약 8.8km
        double d = GeometryUtils.haversine(126.9780, 37.5665, 127.0276, 37.4979);
        assertThat(d).isCloseTo(8_800, within(200.0));
    }

    @Test
    void 위도_1도는_약_111km() {
        assertThat(GeometryUtils.haversine(127, 37, 127, 38)).isCloseTo(111_195, within(100.0));
    }

    @Test
    void 라인스트링_길이는_구간_거리의_합() {
        LineString line = GeometryUtils.FACTORY.createLineString(new Coordinate[]{
                new Coordinate(127, 37), new Coordinate(127, 37.01), new Coordinate(127.01, 37.01)});
        double expected = GeometryUtils.haversine(127, 37, 127, 37.01)
                + GeometryUtils.haversine(127, 37.01, 127.01, 37.01);
        assertThat(GeometryUtils.lengthMeters(line)).isCloseTo(expected, within(1e-6));
    }

    @Test
    void GeoJSON_폴리곤_변환() {
        Map<String, Object> json = GeoJsonMapper.toGeoJson(GeometryUtils.envelope(127, 37, 128, 38));
        assertThat(json.get("type")).isEqualTo("Polygon");
        assertThat((List<?>) json.get("coordinates")).hasSize(1);
    }
}
