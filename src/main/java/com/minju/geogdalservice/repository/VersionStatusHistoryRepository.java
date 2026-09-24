package com.minju.geogdalservice.repository;

import com.minju.geogdalservice.entity.VersionStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VersionStatusHistoryRepository extends JpaRepository<VersionStatusHistory, Long> {

    List<VersionStatusHistory> findByVersionIdOrderByIdAsc(Long versionId);
}
