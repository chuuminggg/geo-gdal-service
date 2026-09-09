package com.minju.geogdalservice.service;

import com.minju.geogdalservice.common.exception.GeoServiceException;
import com.minju.geogdalservice.dto.LocationDto;
import com.minju.geogdalservice.entity.Location;
import com.minju.geogdalservice.repository.LocationRepository;
import com.minju.geogdalservice.util.GeoDistanceUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LocationService {

    private final LocationRepository locationRepository;

    @Transactional
    public LocationDto.Response register(LocationDto.Request request) {
        Location saved = locationRepository.save(Location.builder()
                .name(request.getName())
                .category(request.getCategory())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .description(request.getDescription())
                .build());
        return toDto(saved, null);
    }

    @Transactional
    public List<LocationDto.Response> registerAll(List<LocationDto.Request> requests) {
        return requests.stream().map(this::register).toList();
    }

    @Transactional(readOnly = true)
    public LocationDto.Response get(Long id) {
        return locationRepository.findById(id)
                .map(location -> toDto(location, null))
                .orElseThrow(() -> GeoServiceException.notFound("Location not found: " + id));
    }

    @Transactional
    public void delete(Long id) {
        if (!locationRepository.existsById(id)) {
            throw GeoServiceException.notFound("Location not found: " + id);
        }
        locationRepository.deleteById(id);
    }

    // 반경 내 위치 검색: 경계 사각형으로 1차 조회 → Haversine 거리로 2차 필터 → 가까운 순 정렬
    @Transactional(readOnly = true)
    public List<LocationDto.Response> searchNearby(double lat, double lon, double radiusMeters,
                                                   String category, int limit) {
        double[] box = GeoDistanceUtils.boundingBox(lat, lon, radiusMeters);
        return locationRepository.findInBoundingBox(box[0], box[1], box[2], box[3], category).stream()
                .map(location -> toDto(location,
                        GeoDistanceUtils.haversine(lat, lon, location.getLatitude(), location.getLongitude())))
                .filter(dto -> dto.getDistanceMeters() <= radiusMeters)
                .sorted(Comparator.comparingDouble(LocationDto.Response::getDistanceMeters))
                .limit(limit)
                .toList();
    }

    private LocationDto.Response toDto(Location location, Double distanceMeters) {
        return LocationDto.Response.builder()
                .id(location.getId())
                .name(location.getName())
                .category(location.getCategory())
                .latitude(location.getLatitude())
                .longitude(location.getLongitude())
                .description(location.getDescription())
                .distanceMeters(distanceMeters)
                .build();
    }
}
