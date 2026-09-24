package com.minju.geogdalservice.service;

import com.minju.geogdalservice.dto.ElevationDto;
import com.minju.geogdalservice.dto.ElevationProfileDto;
import com.minju.geogdalservice.gis.GeometryUtils;
import com.minju.geogdalservice.gis.LineSampler;
import com.minju.geogdalservice.gis.OpenRaster;
import com.minju.geogdalservice.service.RasterQueryService.ActiveRaster;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * DEM(수치표고모델) 기반 고도 조회
 */
@Service
@RequiredArgsConstructor
public class ElevationService {

    private static final int MAX_PROFILE_SAMPLES = 5000;
    private static final double DEFAULT_INTERVAL_M = 30;

    private final RasterQueryService rasterQueryService;

    @Value("${geo.elevation.default-dataset:}")
    private String defaultDataset;

    public ElevationDto elevation(String dataset, double lon, double lat) {
        return elevations(dataset, List.<double[]>of(new double[]{lon, lat})).get(0);
    }

    // 여러 좌표를 조회해도 래스터는 한 번만 연다
    public List<ElevationDto> elevations(String dataset, List<double[]> coordinates) {
        coordinates.forEach(c -> GeometryUtils.validateLonLat(c[0], c[1]));
        ActiveRaster raster = rasterQueryService.resolve(resolveDataset(dataset));
        try (OpenRaster dem = rasterQueryService.open(raster)) {
            List<ElevationDto> result = new ArrayList<>(coordinates.size());
            for (double[] c : coordinates) {
                result.add(new ElevationDto(c[0], c[1], dem.interpolate(c[0], c[1])));
            }
            return result;
        }
    }

    /**
     * 경로(폴리라인)를 따라 일정 간격으로 고도를 샘플링하고 누적 오르막/내리막을 계산
     */
    public ElevationProfileDto profile(String dataset, List<double[]> coordinates, Double intervalM) {
        coordinates.forEach(c -> GeometryUtils.validateLonLat(c[0], c[1]));
        List<LineSampler.Sample> samples = LineSampler.sample(coordinates,
                intervalM == null ? DEFAULT_INTERVAL_M : intervalM, MAX_PROFILE_SAMPLES);

        ActiveRaster raster = rasterQueryService.resolve(resolveDataset(dataset));
        List<ElevationProfileDto.ProfilePoint> points = new ArrayList<>(samples.size());
        double ascent = 0;
        double descent = 0;
        Double min = null;
        Double max = null;
        Double previous = null;

        try (OpenRaster dem = rasterQueryService.open(raster)) {
            for (LineSampler.Sample s : samples) {
                Double elevation = dem.interpolate(s.lon(), s.lat());
                points.add(new ElevationProfileDto.ProfilePoint(s.distanceM(), s.lon(), s.lat(), elevation));
                if (elevation == null) {
                    continue;
                }
                if (previous != null) {
                    double diff = elevation - previous;
                    if (diff > 0) ascent += diff;
                    else descent -= diff;
                }
                min = min == null ? elevation : Math.min(min, elevation);
                max = max == null ? elevation : Math.max(max, elevation);
                previous = elevation;
            }
        }

        return ElevationProfileDto.builder()
                .datasetName(raster.datasetName())
                .versionNo(raster.versionNo())
                .lengthM(samples.get(samples.size() - 1).distanceM())
                .ascentM(ascent)
                .descentM(descent)
                .minElevation(min)
                .maxElevation(max)
                .points(points)
                .build();
    }

    public String resolveDataset(String dataset) {
        if (dataset != null && !dataset.isBlank()) {
            return dataset;
        }
        if (defaultDataset == null || defaultDataset.isBlank()) {
            throw new IllegalArgumentException("DEM 데이터셋을 지정해주세요. (dataset 파라미터 또는 geo.elevation.default-dataset)");
        }
        return defaultDataset;
    }

    public boolean hasDefaultDataset() {
        return defaultDataset != null && !defaultDataset.isBlank();
    }
}
