package com.minju.geogdalservice.repository;

import com.minju.geogdalservice.entity.Dataset;
import com.minju.geogdalservice.entity.DatasetType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface DatasetRepository extends JpaRepository<Dataset, Long> {

    Optional<Dataset> findByName(String name);

    boolean existsByName(String name);

    List<Dataset> findAllByOrderByIdAsc();

    // 배포된 버전이 있는 특정 타입의 데이터셋 (경로탐색 네트워크, POI 등 서비스 대상)
    @Query("select d from Dataset d join fetch d.activeVersion where d.type = :type order by d.id")
    List<Dataset> findActiveByType(DatasetType type);
}
