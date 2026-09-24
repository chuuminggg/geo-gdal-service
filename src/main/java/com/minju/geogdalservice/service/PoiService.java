package com.minju.geogdalservice.service;

import com.minju.geogdalservice.dto.PoiDto;
import com.minju.geogdalservice.gis.GeometryUtils;
import com.minju.geogdalservice.repository.PoiRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PoiService {

    static final double MAX_RADIUS_M = 20_000;
    static final int MAX_LIMIT = 200;

    private final PoiRepository poiRepository;

    public List<PoiDto> nearby(double lon, double lat, double radiusM, String category, String dataset, int limit) {
        GeometryUtils.validateLonLat(lon, lat);
        if (radiusM <= 0 || radiusM > MAX_RADIUS_M) {
            throw new IllegalArgumentException("반경은 0 초과 %.0fm 이하여야 합니다.".formatted(MAX_RADIUS_M));
        }
        return poiRepository.findWithinRadius(lon, lat, radiusM, blankToNull(category), blankToNull(dataset),
                checkLimit(limit));
    }

    public List<PoiDto> nearest(double lon, double lat, int k, String category, String dataset) {
        GeometryUtils.validateLonLat(lon, lat);
        return poiRepository.findNearest(lon, lat, checkLimit(k), blankToNull(category), blankToNull(dataset));
    }

    private int checkLimit(int limit) {
        if (limit <= 0 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("조회 개수는 1 ~ %d 사이여야 합니다.".formatted(MAX_LIMIT));
        }
        return limit;
    }

    private String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
