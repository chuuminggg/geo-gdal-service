package com.minju.geogdalservice.repository;

import com.minju.geogdalservice.entity.ValidationResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ValidationResultRepository extends JpaRepository<ValidationResult, Long> {

    List<ValidationResult> findByVersionIdOrderByIdAsc(Long versionId);

    @Modifying
    @Query("delete from ValidationResult r where r.versionId = :versionId")
    void deleteByVersionId(Long versionId);
}
