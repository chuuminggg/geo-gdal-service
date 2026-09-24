package com.minju.geogdalservice.repository;

import com.minju.geogdalservice.dto.PoiDto;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.minju.geogdalservice.repository.RoadNetworkRepository.truncate;

@Repository
@RequiredArgsConstructor
public class PoiRepository {

    private static final int BATCH_SIZE = 1000;

    // 서비스 중인(active) 버전의 POI 만 조회 대상
    private static final String ACTIVE_POI = """
            FROM poi p
            JOIN dataset d ON d.active_version_id = p.version_id
            WHERE (CAST(:category AS varchar) IS NULL OR p.category = :category)
              AND (CAST(:dataset AS varchar) IS NULL OR d.name = :dataset)
            """;

    private static final String SELECT_COLUMNS = """
            SELECT p.id, d.name AS dataset_name, p.name, p.category, p.address,
                   ST_X(p.geom) AS lon, ST_Y(p.geom) AS lat,
                   ST_Distance(p.geom::geography, ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography) AS distance_m
            """;

    private static final RowMapper<PoiDto> POI_ROW_MAPPER = (rs, i) -> PoiDto.builder()
            .id(rs.getLong("id"))
            .datasetName(rs.getString("dataset_name"))
            .name(rs.getString("name"))
            .category(rs.getString("category"))
            .address(rs.getString("address"))
            .lon(rs.getDouble("lon"))
            .lat(rs.getDouble("lat"))
            .distanceM(rs.getDouble("distance_m"))
            .build();

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbcTemplate;

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

    /**
     * 반경(m) 내 POI. geography 표현식 GiST 인덱스(idx_poi_geog)로 ST_DWithin 을 인덱스 검색한다.
     */
    public List<PoiDto> findWithinRadius(double lon, double lat, double radiusM, String category, String dataset, int limit) {
        String sql = SELECT_COLUMNS + ACTIVE_POI + """
                  AND ST_DWithin(p.geom::geography, ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography, :radius)
                ORDER BY distance_m
                LIMIT :limit
                """;
        return namedJdbcTemplate.query(sql, params(lon, lat, category, dataset)
                .addValue("radius", radiusM)
                .addValue("limit", limit), POI_ROW_MAPPER);
    }

    /**
     * 가장 가까운 K개 POI. geography <-> 연산자로 인덱스 기반 KNN 검색 (전체 정렬 없음)
     */
    public List<PoiDto> findNearest(double lon, double lat, int k, String category, String dataset) {
        String sql = SELECT_COLUMNS + ACTIVE_POI + """
                ORDER BY p.geom::geography <-> ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography
                LIMIT :k
                """;
        return namedJdbcTemplate.query(sql, params(lon, lat, category, dataset).addValue("k", k), POI_ROW_MAPPER);
    }

    private MapSqlParameterSource params(double lon, double lat, String category, String dataset) {
        return new MapSqlParameterSource()
                .addValue("lon", lon)
                .addValue("lat", lat)
                .addValue("category", category)
                .addValue("dataset", dataset);
    }

    public record PoiRow(String sourceId, String name, String category, String address, double lon, double lat) {
    }
}
