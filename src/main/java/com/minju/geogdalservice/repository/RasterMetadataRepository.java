package com.minju.geogdalservice.repository;

import com.minju.geogdalservice.entity.RasterMetadata;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RasterMetadataRepository extends JpaRepository<RasterMetadata, Long> {
}
