package com.minju.geogdalservice.network;

import com.minju.geogdalservice.gis.GeometryUtils;
import com.minju.geogdalservice.gis.VectorFeature;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;

import java.util.List;
import java.util.Map;

import static com.minju.geogdalservice.support.TestFeatures.line;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class RoadNetworkBuilderTest {

    @Test
    void 끝점을_공유하는_링크는_같은_노드로_연결된다() {
        // A(127.000,37.500) - B(127.001,37.500) - C(127.001,37.501)
        RoadNetwork network = RoadNetworkBuilder.build(List.of(
                line(127.000, 37.500, 127.001, 37.500),
                line(127.001, 37.500, 127.001, 37.501)));

        assertThat(network.nodes()).hasSize(3);
        assertThat(network.links()).hasSize(2);
        assertThat(network.links().get(0).toNode()).isEqualTo(network.links().get(1).fromNode());
    }

    @Test
    void 약_1cm_이내_좌표_오차는_같은_노드로_스냅된다() {
        RoadNetwork network = RoadNetworkBuilder.build(List.of(
                line(127.000, 37.500, 127.001, 37.500),
                line(127.00100000004, 37.50000000003, 127.001, 37.501)));

        assertThat(network.nodes()).hasSize(3);
    }

    @Test
    void 일방통행_속성을_해석하고_역방향은_geometry를_뒤집는다() {
        RoadNetwork network = RoadNetworkBuilder.build(List.of(
                line(Map.of("oneway", "yes"), 127.000, 37.5, 127.001, 37.5),
                line(Map.of("oneway", "-1"), 127.001, 37.5, 127.002, 37.5),
                line(Map.of("oneway", "no"), 127.002, 37.5, 127.003, 37.5)));

        RoadLinkData forward = network.links().get(0);
        RoadLinkData reverse = network.links().get(1);
        RoadLinkData twoWay = network.links().get(2);

        assertThat(forward.oneway()).isTrue();
        assertThat(reverse.oneway()).isTrue();
        // 역방향 일방통행: 127.002 -> 127.001 방향으로 저장
        assertThat(network.nodes().get(reverse.fromNode()).x).isEqualTo(127.002);
        assertThat(reverse.geometry().getCoordinateN(0).x).isEqualTo(127.002);
        assertThat(twoWay.oneway()).isFalse();
    }

    @Test
    void 속성명이_달라도_도로등급_이름_제한속도를_매핑한다() {
        RoadNetwork network = RoadNetworkBuilder.build(List.of(
                line(Map.of("highway", "primary", "name", "테헤란로", "maxspeed", "60 km/h", "osm_id", "123"),
                        127.0, 37.5, 127.001, 37.5),
                line(Map.of("road_rank", "103", "max_spd", "30 mph"), 127.001, 37.5, 127.002, 37.5)));

        RoadLinkData a = network.links().get(0);
        assertThat(a.roadClass()).isEqualTo("primary");
        assertThat(a.name()).isEqualTo("테헤란로");
        assertThat(a.maxSpeedKph()).isEqualTo(60);
        assertThat(a.sourceId()).isEqualTo("123");
        assertThat(network.links().get(1).maxSpeedKph()).isEqualTo(48);
        assertThat(network.links().get(1).roadClass()).isEqualTo("103");
    }

    @Test
    void 멀티라인은_파트별_링크로_분리하고_라인이_아닌_피처는_제외한다() {
        LineString l1 = GeometryUtils.FACTORY.createLineString(new Coordinate[]{
                new Coordinate(127, 37.5), new Coordinate(127.001, 37.5)});
        LineString l2 = GeometryUtils.FACTORY.createLineString(new Coordinate[]{
                new Coordinate(127.001, 37.5), new Coordinate(127.002, 37.5)});
        VectorFeature multi = new VectorFeature(
                GeometryUtils.FACTORY.createMultiLineString(new LineString[]{l1, l2}), Map.of());
        VectorFeature point = new VectorFeature(GeometryUtils.point(127, 37.5), Map.of());

        RoadNetwork network = RoadNetworkBuilder.build(List.of(multi, point));

        assertThat(network.links()).hasSize(2);
        assertThat(network.skippedFeatures()).isEqualTo(1);
    }

    @Test
    void 링크_길이는_미터_단위로_계산된다() {
        RoadNetwork network = RoadNetworkBuilder.build(List.of(line(127.0, 37.0, 127.0, 37.01)));
        assertThat(network.links().get(0).lengthM()).isCloseTo(1112, within(2.0));
    }

    @Test
    void 끊어진_도로망의_연결_요소를_계산한다() {
        RoadNetwork network = RoadNetworkBuilder.build(List.of(
                line(127.000, 37.5, 127.001, 37.5),
                line(127.001, 37.5, 127.002, 37.5),
                // 떨어진 섬
                line(128.000, 36.0, 128.001, 36.0)));

        RoadNetwork.Connectivity c = network.connectivity();
        assertThat(c.componentCount()).isEqualTo(2);
        assertThat(c.largestComponentSize()).isEqualTo(3);
        assertThat(c.largestComponentRatio()).isCloseTo(0.6, within(1e-9));
    }

    @Test
    void 제한속도_파싱() {
        assertThat(RoadNetworkBuilder.speedKph("50")).isEqualTo(50);
        assertThat(RoadNetworkBuilder.speedKph("unknown")).isNull();
        assertThat(RoadNetworkBuilder.speedKph(null)).isNull();
        assertThat(RoadNetworkBuilder.speedKph("0")).isNull();
    }
}
