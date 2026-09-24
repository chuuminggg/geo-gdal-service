package com.minju.geogdalservice.controller;

import com.minju.geogdalservice.common.dto.CommonResponse;
import com.minju.geogdalservice.dto.RouteComparisonDto;
import com.minju.geogdalservice.dto.RouteDto;
import com.minju.geogdalservice.routing.RouteService;
import com.minju.geogdalservice.routing.ShortestPath;
import com.minju.geogdalservice.routing.TravelMode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/routes")
@RequiredArgsConstructor
public class RouteController {

    private final RouteService routeService;

    /**
     * 경로탐색 (최소 시간). mode: CAR | WALK | BIKE, algorithm: ASTAR | DIJKSTRA
     */
    @GetMapping
    public CommonResponse<RouteDto> route(@RequestParam double fromLon, @RequestParam double fromLat,
                                          @RequestParam double toLon, @RequestParam double toLat,
                                          @RequestParam(defaultValue = "CAR") TravelMode mode,
                                          @RequestParam(defaultValue = "ASTAR") ShortestPath.Algorithm algorithm,
                                          @RequestParam(required = false) String dataset) {
        return CommonResponse.success(routeService.route(dataset, fromLon, fromLat, toLon, toLat, mode, algorithm));
    }

    /**
     * 같은 구간을 Dijkstra 와 A* 로 탐색해 결과(최적성)와 탐색 노드 수를 비교
     */
    @GetMapping("/compare")
    public CommonResponse<RouteComparisonDto> compare(@RequestParam double fromLon, @RequestParam double fromLat,
                                                      @RequestParam double toLon, @RequestParam double toLat,
                                                      @RequestParam(defaultValue = "CAR") TravelMode mode,
                                                      @RequestParam(required = false) String dataset) {
        return CommonResponse.success(routeService.compare(dataset, fromLon, fromLat, toLon, toLat, mode));
    }
}
