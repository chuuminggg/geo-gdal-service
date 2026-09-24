package com.minju.geogdalservice.repository;

import com.minju.geogdalservice.entity.DatasetVersion;
import com.minju.geogdalservice.entity.VersionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DatasetVersionRepository extends JpaRepository<DatasetVersion, Long> {

    List<DatasetVersion> findByDatasetIdOrderByVersionNoDesc(Long datasetId);

    Optional<DatasetVersion> findByDatasetIdAndVersionNo(Long datasetId, Integer versionNo);

    @Query("select coalesce(max(v.versionNo), 0) from DatasetVersion v where v.dataset.id = :datasetId")
    int findMaxVersionNo(@Param("datasetId") Long datasetId);

    // 동일 데이터셋에 같은 파일(체크섬)이 이미 올라와 있는지 (자기 자신·불합격 버전 제외)
    boolean existsByDatasetIdAndChecksumAndIdNotAndStatusNotIn(Long datasetId, String checksum, Long id,
                                                               Collection<VersionStatus> excluded);

    /**
     * 배포 중인(active) 버전 중 footprint가 bbox와 겹치는 버전 검색.
     * ST_MakeEnvelope + ST_Intersects 는 footprint GiST 인덱스를 사용한다.
     */
    @Query(value = """
            SELECT v.* FROM dataset_version v
            JOIN dataset d ON d.active_version_id = v.id
            WHERE ST_Intersects(v.footprint, ST_MakeEnvelope(:minLon, :minLat, :maxLon, :maxLat, 4326))
              AND (CAST(:type AS varchar) IS NULL OR d.type = CAST(:type AS varchar))
            ORDER BY d.name
            """, nativeQuery = true)
    List<DatasetVersion> searchActiveIntersecting(@Param("minLon") double minLon, @Param("minLat") double minLat,
                                                  @Param("maxLon") double maxLon, @Param("maxLat") double maxLat,
                                                  @Param("type") String type);

    // 서버 재시작 등으로 진행 중 상태에 멈춘 버전 복구용
    List<DatasetVersion> findByStatusIn(Collection<VersionStatus> statuses);
}
