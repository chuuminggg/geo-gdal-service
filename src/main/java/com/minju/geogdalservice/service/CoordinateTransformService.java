package com.minju.geogdalservice.service;

import com.minju.geogdalservice.common.exception.GeoServiceException;
import com.minju.geogdalservice.dto.CoordinateTransformDto;
import com.minju.geogdalservice.util.GdalRasterSupport;
import lombok.RequiredArgsConstructor;
import org.gdal.osr.CoordinateTransformation;
import org.gdal.osr.SpatialReference;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CoordinateTransformService {

    private final GdalRasterSupport gdalRasterSupport;

    // 좌표계 변환 (x=경도/Easting, y=위도/Northing 순서)
    public CoordinateTransformDto.Response transform(CoordinateTransformDto.Request request) {
        SpatialReference source = gdalRasterSupport.createSpatialReference(request.getSourceCrs());
        SpatialReference target = gdalRasterSupport.createSpatialReference(request.getTargetCrs());
        CoordinateTransformation transformation = gdalRasterSupport.createTransformation(source, target);

        List<CoordinateTransformDto.Point> result = new ArrayList<>(request.getPoints().size());
        for (CoordinateTransformDto.Point point : request.getPoints()) {
            double z = point.getZ() != null ? point.getZ() : 0.0;
            double[] transformed;
            try {
                transformed = transformation.TransformPoint(point.getX(), point.getY(), z);
            } catch (RuntimeException e) {
                throw GeoServiceException.badRequest(
                        "Failed to transform point (x=" + point.getX() + ", y=" + point.getY() + "): " + e.getMessage());
            }
            if (Double.isInfinite(transformed[0]) || Double.isInfinite(transformed[1])) {
                throw GeoServiceException.badRequest(
                        "Point (x=" + point.getX() + ", y=" + point.getY() + ") is outside the valid area of the CRS");
            }
            result.add(CoordinateTransformDto.Point.builder()
                    .x(transformed[0])
                    .y(transformed[1])
                    .z(point.getZ() != null ? transformed[2] : null)
                    .build());
        }

        return CoordinateTransformDto.Response.builder()
                .sourceCrs(request.getSourceCrs())
                .targetCrs(request.getTargetCrs())
                .points(result)
                .build();
    }
}
