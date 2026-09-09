package com.minju.geogdalservice.repository;

import com.minju.geogdalservice.entity.Location;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LocationRepository extends JpaRepository<Location, Long> {

    // 반경 검색 1차 필터: 경계 사각형(Bounding Box) 내 위치 조회
    @Query("SELECT l FROM Location l WHERE " +
            "l.latitude BETWEEN :minLat AND :maxLat AND " +
            "l.longitude BETWEEN :minLon AND :maxLon AND " +
            "(:category IS NULL OR l.category = :category)")
    List<Location> findInBoundingBox(
            @Param("minLat") double minLat,
            @Param("maxLat") double maxLat,
            @Param("minLon") double minLon,
            @Param("maxLon") double maxLon,
            @Param("category") String category
    );
}
