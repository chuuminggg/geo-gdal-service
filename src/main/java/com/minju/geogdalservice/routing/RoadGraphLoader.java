package com.minju.geogdalservice.routing;

import com.minju.geogdalservice.dto.ElevationDto;
import com.minju.geogdalservice.gis.GeometryUtils;
import com.minju.geogdalservice.network.RoadLinkData;
import com.minju.geogdalservice.service.ElevationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKBReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * PostGIS 의 도로 네트워크 버전을 읽어 메모리 그래프를 만든다.
 * DEM 이 배포되어 있으면 노드 고도를 샘플링해 보행/자전거 경사 비용에 반영한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoadGraphLoader {

    private final JdbcTemplate jdbcTemplate;
    private final ElevationService elevationService;

    // 경사 반영에 사용할 DEM 데이터셋 (비어 있으면 고도 미반영)
    @Value("${geo.routing.elevation-dataset:${geo.elevation.default-dataset:}}")
    private String elevationDataset;

    public RoadGraph load(long versionId, String datasetName, int versionNo) {
        long start = System.currentTimeMillis();

        List<Coordinate> nodes = new ArrayList<>();
        jdbcTemplate.query("SELECT node_seq, ST_X(geom) AS lon, ST_Y(geom) AS lat FROM road_node "
                        + "WHERE version_id = ? ORDER BY node_seq",
                rs -> {
                    if (rs.getInt("node_seq") != nodes.size()) {
                        throw new IllegalStateException("road_node.node_seq 가 연속적이지 않습니다. version=" + versionId);
                    }
                    nodes.add(new Coordinate(rs.getDouble("lon"), rs.getDouble("lat")));
                }, versionId);

        WKBReader wkbReader = new WKBReader(GeometryUtils.FACTORY);
        List<RoadLinkData> links = new ArrayList<>();
        jdbcTemplate.query("SELECT source_id, from_node_seq, to_node_seq, road_class, road_name, oneway, "
                        + "max_speed_kph, length_m, ST_AsBinary(geom) AS wkb FROM road_link WHERE version_id = ? ORDER BY id",
                rs -> {
                    LineString geometry;
                    try {
                        geometry = (LineString) wkbReader.read(rs.getBytes("wkb"));
                    } catch (ParseException e) {
                        throw new IllegalStateException("road_link geometry 해석 실패", e);
                    }
                    Integer maxSpeed = rs.getObject("max_speed_kph", Integer.class);
                    links.add(new RoadLinkData(
                            rs.getString("source_id"),
                            rs.getInt("from_node_seq"),
                            rs.getInt("to_node_seq"),
                            rs.getString("road_class"),
                            rs.getString("road_name"),
                            rs.getBoolean("oneway"),
                            maxSpeed,
                            rs.getDouble("length_m"),
                            geometry));
                }, versionId);

        RoadGraph graph = RoadGraph.builder()
                .version(versionId, datasetName, versionNo)
                .nodes(nodes)
                .links(links)
                .elevations(sampleElevations(nodes))
                .build();

        log.info("Road graph loaded: dataset={}, v{}, nodes={}, edges={}, elevation={}, {}ms",
                datasetName, versionNo, graph.nodeCount(), graph.edgeCount(), graph.hasElevation(),
                System.currentTimeMillis() - start);
        return graph;
    }

    private double[] sampleElevations(List<Coordinate> nodes) {
        if (elevationDataset == null || elevationDataset.isBlank() || nodes.isEmpty()) {
            return null;
        }
        try {
            List<double[]> coords = nodes.stream().map(c -> new double[]{c.x, c.y}).toList();
            List<ElevationDto> elevations = elevationService.elevations(elevationDataset, coords);
            double[] result = new double[nodes.size()];
            for (int i = 0; i < result.length; i++) {
                Double e = elevations.get(i).getElevation();
                result[i] = e == null ? Double.NaN : e;
            }
            return result;
        } catch (RuntimeException e) {
            // DEM 이 아직 배포되지 않은 경우 등: 경사 없이 경로탐색은 계속 제공
            log.warn("Elevation sampling skipped for road graph: {}", e.getMessage());
            return null;
        }
    }

    public String elevationDataset() {
        return elevationDataset;
    }
}
