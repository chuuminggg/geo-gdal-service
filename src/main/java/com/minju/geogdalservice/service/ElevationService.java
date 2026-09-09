package com.minju.geogdalservice.service;

import com.minju.geogdalservice.common.exception.GeoServiceException;
import com.minju.geogdalservice.dto.ElevationDto;
import com.minju.geogdalservice.dto.ElevationDto.Interpolation;
import com.minju.geogdalservice.util.GdalRasterSupport;
import com.minju.geogdalservice.util.GdalRasterSupport.PixelLocator;
import com.minju.geogdalservice.util.GdalRasterSupport.PixelPosition;
import lombok.RequiredArgsConstructor;
import org.gdal.gdal.Band;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ElevationService {

    private final GdalRasterSupport gdalRasterSupport;

    // DEM에서 위경도 지점들의 고도 조회
    public ElevationDto.Response getElevations(String fileName, List<ElevationDto.Point> points,
                                               Interpolation interpolation, Integer band) {
        Interpolation method = interpolation != null ? interpolation : Interpolation.NEAREST;
        int bandIndex = band != null ? band : 1;

        return gdalRasterSupport.withDataset(fileName, (path, dataset) -> {
            if (bandIndex < 1 || bandIndex > dataset.GetRasterCount()) {
                throw GeoServiceException.badRequest("band must be between 1 and " + dataset.GetRasterCount());
            }
            Band rasterBand = dataset.GetRasterBand(bandIndex);
            PixelLocator locator = gdalRasterSupport.createPixelLocator(dataset);

            List<ElevationDto.Result> results = points.stream()
                    .map(point -> sample(rasterBand, locator.locate(point.getLon(), point.getLat()), method)
                            .toResult(point.getLat(), point.getLon()))
                    .toList();

            String unit = rasterBand.GetUnitType();
            return ElevationDto.Response.builder()
                    .fileName(fileName)
                    .band(bandIndex)
                    .unit(unit == null || unit.isBlank() ? null : unit)
                    .interpolation(method)
                    .results(results)
                    .build();
        });
    }

    private Sample sample(Band band, PixelPosition position, Interpolation interpolation) {
        if (!position.isInside()) {
            return Sample.of(ElevationDto.Status.OUT_OF_BOUNDS, null);
        }
        if (interpolation == Interpolation.BILINEAR) {
            Double value = bilinear(band, position);
            if (value != null) {
                return Sample.of(ElevationDto.Status.OK, gdalRasterSupport.applyScaleOffset(band, value));
            }
            // 주변 픽셀에 NoData가 섞여 있으면 최근접 값으로 대체
        }
        double raw = gdalRasterSupport.readRawValue(band, position.col(), position.row());
        if (gdalRasterSupport.isNoData(band, raw)) {
            return Sample.of(ElevationDto.Status.NO_DATA, null);
        }
        return Sample.of(ElevationDto.Status.OK, gdalRasterSupport.applyScaleOffset(band, raw));
    }

    // 픽셀 중심 기준 이중선형 보간. 주변 4개 픽셀 중 NoData가 있으면 null
    private Double bilinear(Band band, PixelPosition position) {
        double fx = position.x() - 0.5;
        double fy = position.y() - 0.5;
        int x0 = (int) Math.floor(fx);
        int y0 = (int) Math.floor(fy);
        double dx = fx - x0;
        double dy = fy - y0;

        int left = clamp(x0, position.width());
        int right = clamp(x0 + 1, position.width());
        int top = clamp(y0, position.height());
        int bottom = clamp(y0 + 1, position.height());

        double topLeft = gdalRasterSupport.readRawValue(band, left, top);
        double topRight = gdalRasterSupport.readRawValue(band, right, top);
        double bottomLeft = gdalRasterSupport.readRawValue(band, left, bottom);
        double bottomRight = gdalRasterSupport.readRawValue(band, right, bottom);
        for (double value : new double[]{topLeft, topRight, bottomLeft, bottomRight}) {
            if (gdalRasterSupport.isNoData(band, value)) {
                return null;
            }
        }

        double topValue = topLeft * (1 - dx) + topRight * dx;
        double bottomValue = bottomLeft * (1 - dx) + bottomRight * dx;
        return topValue * (1 - dy) + bottomValue * dy;
    }

    private int clamp(int index, int size) {
        return Math.max(0, Math.min(index, size - 1));
    }

    private record Sample(ElevationDto.Status status, Double value) {

        static Sample of(ElevationDto.Status status, Double value) {
            return new Sample(status, value);
        }

        ElevationDto.Result toResult(double lat, double lon) {
            return ElevationDto.Result.builder()
                    .lat(lat)
                    .lon(lon)
                    .elevation(value)
                    .status(status)
                    .build();
        }
    }
}
