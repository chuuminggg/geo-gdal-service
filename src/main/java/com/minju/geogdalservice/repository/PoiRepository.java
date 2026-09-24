package com.minju.geogdalservice.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.minju.geogdalservice.repository.RoadNetworkRepository.truncate;

@Repository
@RequiredArgsConstructor
public class PoiRepository {

    private static final int BATCH_SIZE = 1000;

    private final JdbcTemplate jdbcTemplate;

    public void deleteByVersion(long versionId) {
        jdbcTemplate.update("DELETE FROM poi WHERE version_id = ?", versionId);
    }

    public void insert(long versionId, List<PoiRow> pois) {
        jdbcTemplate.batchUpdate("""
                        INSERT INTO poi (version_id, source_id, name, category, address, geom)
                        VALUES (?, ?, ?, ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326))
                        """,
                pois, BATCH_SIZE,
                (ps, poi) -> {
                    ps.setLong(1, versionId);
                    ps.setString(2, truncate(poi.sourceId(), 64));
                    ps.setString(3, truncate(poi.name(), 200));
                    ps.setString(4, truncate(poi.category(), 50));
                    ps.setString(5, truncate(poi.address(), 300));
                    ps.setDouble(6, poi.lon());
                    ps.setDouble(7, poi.lat());
                });
    }

    public record PoiRow(String sourceId, String name, String category, String address, double lon, double lat) {
    }
}
