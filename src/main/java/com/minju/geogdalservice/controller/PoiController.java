package com.minju.geogdalservice.controller;

import com.minju.geogdalservice.common.dto.CommonResponse;
import com.minju.geogdalservice.dto.PoiDto;
import com.minju.geogdalservice.service.PoiService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pois")
@RequiredArgsConstructor
public class PoiController {

    private final PoiService poiService;

    /**
     * 반경 내 위치 데이터 검색 (거리순)
     */
    @GetMapping("/nearby")
    public CommonResponse<List<PoiDto>> nearby(@RequestParam double lon, @RequestParam double lat,
                                               @RequestParam(defaultValue = "500") double radius,
                                               @RequestParam(required = false) String category,
                                               @RequestParam(required = false) String dataset,
                                               @RequestParam(defaultValue = "50") int limit) {
        return CommonResponse.success(poiService.nearby(lon, lat, radius, category, dataset, limit));
    }

    /**
     * 가장 가까운 K개 (KNN)
     */
    @GetMapping("/nearest")
    public CommonResponse<List<PoiDto>> nearest(@RequestParam double lon, @RequestParam double lat,
                                                @RequestParam(defaultValue = "5") int k,
                                                @RequestParam(required = false) String category,
                                                @RequestParam(required = false) String dataset) {
        return CommonResponse.success(poiService.nearest(lon, lat, k, category, dataset));
    }
}
