package com.minju.geogdalservice.repository;

import com.minju.geogdalservice.network.RoadNetwork;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.io.WKBWriter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Types;
import java.util.List;
import java.util.stream.IntStream;

/**
 * 도로 네트워크 대량 적재/조회.
 * 수만 건 단위 적재는 JPA 엔티티 단건 persist 대신 JDBC batch insert 로 처리한다.
 */
@Repository
@RequiredArgsConstructor
public class RoadNetworkRepository {

    private static final int BATCH_SIZE = 1000;

    private final JdbcTemplate jdbcTemplate;

    public void deleteByVersion(long versionId) {
        jdbcTemplate.update("DELETE FROM road_link WHERE version_id = ?", versionId);
        jdbcTemplate.update("DELETE FROM road_node WHERE version_id = ?", versionId);
    }

    public void insert(long versionId, RoadNetwork network) {
        List<Coordinate> nodes = network.nodes();
        jdbcTemplate.batchUpdate(
                "INSERT INTO road_node (version_id, node_seq, geom) VALUES (?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326))",
                IntStream.range(0, nodes.size()).boxed().toList(), BATCH_SIZE,
                (ps, seq) -> {
                    ps.setLong(1, versionId);
                    ps.setInt(2, seq);
                    ps.setDouble(3, nodes.get(seq).x);
                    ps.setDouble(4, nodes.get(seq).y);
                });

        WKBWriter wkbWriter = new WKBWriter(2);
        jdbcTemplate.batchUpdate("""
                        INSERT INTO road_link (version_id, source_id, from_node_seq, to_node_seq, road_class, road_name,
                                               oneway, max_speed_kph, length_m, geom)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ST_GeomFromWKB(?, 4326))
                        """,
                network.links(), BATCH_SIZE,
                (ps, link) -> {
                    ps.setLong(1, versionId);
                    ps.setString(2, truncate(link.sourceId(), 64));
                    ps.setInt(3, link.fromNode());
                    ps.setInt(4, link.toNode());
                    ps.setString(5, truncate(link.roadClass(), 30));
                    ps.setString(6, truncate(link.name(), 200));
                    ps.setBoolean(7, link.oneway());
                    if (link.maxSpeedKph() == null) {
                        ps.setNull(8, Types.INTEGER);
                    } else {
                        ps.setInt(8, link.maxSpeedKph());
                    }
                    ps.setDouble(9, link.lengthM());
                    ps.setBytes(10, wkbWriter.write(link.geometry()));
                });
    }

    public int countLinks(long versionId) {
        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM road_link WHERE version_id = ?",
                Integer.class, versionId);
        return count == null ? 0 : count;
    }

    static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
