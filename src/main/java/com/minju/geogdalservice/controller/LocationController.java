package com.minju.geogdalservice.controller;

import com.minju.geogdalservice.common.dto.CommonResponse;
import com.minju.geogdalservice.dto.LocationDto;
import com.minju.geogdalservice.service.LocationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/locations")
@RequiredArgsConstructor
public class LocationController {

    private final LocationService locationService;

    // 위치 등록
    @PostMapping
    public CommonResponse<LocationDto.Response> register(@RequestBody @Valid LocationDto.Request request) {
        return CommonResponse.success(locationService.register(request));
    }

    // 위치 일괄 등록
    @PostMapping("/bulk")
    public CommonResponse<List<LocationDto.Response>> registerAll(
            @RequestBody @NotEmpty(message = "locations must not be empty") List<LocationDto.@Valid Request> requests
    ) {
        return CommonResponse.success(locationService.registerAll(requests));
    }

    @GetMapping("/{id}")
    public CommonResponse<LocationDto.Response> get(@PathVariable Long id) {
        return CommonResponse.success(locationService.get(id));
    }

    @DeleteMapping("/{id}")
    public CommonResponse<Void> delete(@PathVariable Long id) {
        locationService.delete(id);
        return CommonResponse.success();
    }

    // 반경 내 위치 검색 (radius 단위: m)
    @GetMapping("/nearby")
    public CommonResponse<List<LocationDto.Response>> searchNearby(
            @RequestParam @DecimalMin(value = "-90", message = "lat must be >= -90")
            @DecimalMax(value = "90", message = "lat must be <= 90") double lat,
            @RequestParam @DecimalMin(value = "-180", message = "lon must be >= -180")
            @DecimalMax(value = "180", message = "lon must be <= 180") double lon,
            @RequestParam @DecimalMin(value = "0", inclusive = false, message = "radius must be > 0")
            @DecimalMax(value = "20037508", message = "radius must be <= 20037508") double radius,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "100") @Min(value = 1, message = "limit must be >= 1")
            @Max(value = 1000, message = "limit must be <= 1000") int limit
    ) {
        return CommonResponse.success(locationService.searchNearby(lat, lon, radius, category, limit));
    }
}
