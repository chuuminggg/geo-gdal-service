package com.minju.geogdalservice.repository;

import com.minju.geogdalservice.entity.Dataset;
import com.minju.geogdalservice.entity.DatasetType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface DatasetRepository extends JpaRepository<Dataset, Long> {

    Optional<Dataset> findByName(String name);

    // 버전 번호 채번·배포 전환을 데이터셋 단위로 직렬화하기 위한 행 잠금 (SELECT ... FOR UPDATE)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Dataset d where d.id = :id")
    Optional<Dataset> findByIdForUpdate(Long id);

    boolean existsByName(String name);

    List<Dataset> findAllByOrderByIdAsc();

    @Query("select d from Dataset d left join fetch d.activeVersion where d.name = :name")
    Optional<Dataset> findWithActiveVersionByName(String name);

    // 배포된 버전이 있는 특정 타입의 데이터셋 (경로탐색 네트워크, POI 등 서비스 대상)
    @Query("select d from Dataset d join fetch d.activeVersion where d.type = :type order by d.id")
    List<Dataset> findActiveByType(DatasetType type);
}
