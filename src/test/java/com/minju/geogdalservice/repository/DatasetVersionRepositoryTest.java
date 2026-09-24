package com.minju.geogdalservice.repository;

import com.minju.geogdalservice.entity.*;
import com.minju.geogdalservice.gis.GeometryUtils;
import com.minju.geogdalservice.support.PostgisTestcontainersConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgisTestcontainersConfig.class)
@Testcontainers(disabledWithoutDocker = true)
class DatasetVersionRepositoryTest {

    @Autowired
    DatasetRepository datasetRepository;
    @Autowired
    DatasetVersionRepository versionRepository;
    @Autowired
    RasterMetadataRepository rasterMetadataRepository;
    @Autowired
    EntityManager em;

    @Test
    void 공간_타입과_배열_컬럼이_PostGIS에_저장되고_조회된다() {
        DatasetVersion version = publishedRaster("seoul-dem", 126.8, 37.4, 127.2, 37.7);
        rasterMetadataRepository.save(RasterMetadata.builder()
                .version(version).width(100).height(80).bandCount(1).dataType("Float32").epsg(5186)
                .geoTransform(new double[]{200000, 30, 0, 560000, 0, -30})
                .build());
        em.flush();
        em.clear();

        DatasetVersion found = versionRepository.findById(version.getId()).orElseThrow();
        assertThat(found.getFootprint().getSRID()).isEqualTo(4326);
        assertThat(found.getFootprint().getEnvelopeInternal().getMinX()).isEqualTo(126.8);
        assertThat(rasterMetadataRepository.findById(version.getId()).orElseThrow().getGeoTransform())
                .containsExactly(200000, 30, 0, 560000, 0, -30);
    }

    @Test
    void bbox와_겹치는_배포_버전만_검색된다() {
        publishedRaster("seoul-dem", 126.8, 37.4, 127.2, 37.7);
        publishedRaster("busan-dem", 128.9, 35.0, 129.3, 35.3);
        em.flush();

        // 강남 일대 bbox
        List<DatasetVersion> result = versionRepository.searchActiveIntersecting(127.0, 37.45, 127.1, 37.55, null);
        assertThat(result).extracting(v -> v.getDataset().getName()).containsExactly("seoul-dem");

        assertThat(versionRepository.searchActiveIntersecting(127.0, 37.45, 127.1, 37.55, "POI")).isEmpty();
    }

    @Test
    void 같은_체크섬의_유효한_버전이_있으면_중복으로_판단한다() {
        DatasetVersion v1 = publishedRaster("seoul-dem", 126.8, 37.4, 127.2, 37.7);
        Dataset dataset = v1.getDataset();
        DatasetVersion v2 = versionRepository.save(DatasetVersion.builder()
                .dataset(dataset).versionNo(2).originalFileName("dem.tif").rawKey("raw/2")
                .checksum(v1.getChecksum()).fileSize(10).build());
        em.flush();

        assertThat(versionRepository.existsByDatasetIdAndChecksumAndIdNotAndStatusNotIn(
                dataset.getId(), v2.getChecksum(), v2.getId(), Set.of(VersionStatus.REJECTED))).isTrue();
        assertThat(versionRepository.findMaxVersionNo(dataset.getId())).isEqualTo(2);
    }

    private DatasetVersion publishedRaster(String name, double minLon, double minLat, double maxLon, double maxLat) {
        Dataset dataset = datasetRepository.save(Dataset.builder().name(name).type(DatasetType.RASTER).build());
        DatasetVersion version = DatasetVersion.builder()
                .dataset(dataset).versionNo(1).originalFileName(name + ".tif").rawKey("raw/" + name)
                .checksum("a".repeat(64)).fileSize(1024).build();
        version.updateSpatialInfo(GeometryUtils.envelope(minLon, minLat, maxLon, maxLat), null);
        versionRepository.save(version);
        dataset.changeActiveVersion(version);
        return version;
    }
}
