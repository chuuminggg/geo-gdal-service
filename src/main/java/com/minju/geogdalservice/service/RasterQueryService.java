package com.minju.geogdalservice.service;

import com.minju.geogdalservice.common.exception.NotFoundException;
import com.minju.geogdalservice.config.GdalS3Config;
import com.minju.geogdalservice.dto.PixelValueDto;
import com.minju.geogdalservice.entity.Dataset;
import com.minju.geogdalservice.entity.DatasetType;
import com.minju.geogdalservice.entity.DatasetVersion;
import com.minju.geogdalservice.gis.GeometryUtils;
import com.minju.geogdalservice.gis.OpenRaster;
import com.minju.geogdalservice.repository.DatasetRepository;
import com.minju.geogdalservice.util.GdalInitializer;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;

/**
 * 배포된(active) 래스터 버전의 COG 를 /vsis3/ 로 열어 조회한다.
 */
@Service
@RequiredArgsConstructor
public class RasterQueryService {

    private final DatasetRepository datasetRepository;
    private final GdalInitializer gdalInitializer;

    @Value("${aws.s3.bucket.name}")
    private String bucketName;

    public record ActiveRaster(String datasetName, int versionNo, String gdalPath) {
    }

    public ActiveRaster resolve(String datasetName) {
        Dataset dataset = datasetRepository.findWithActiveVersionByName(datasetName)
                .orElseThrow(() -> new NotFoundException("데이터셋을 찾을 수 없습니다: " + datasetName));
        if (dataset.getType() != DatasetType.RASTER) {
            throw new IllegalArgumentException("래스터 데이터셋이 아닙니다: " + datasetName);
        }
        DatasetVersion active = dataset.getActiveVersion();
        if (active == null || active.getProcessedKey() == null) {
            throw new NotFoundException("배포된 버전이 없습니다: " + datasetName);
        }
        return new ActiveRaster(datasetName, active.getVersionNo(),
                GdalS3Config.vsis3Path(bucketName, active.getProcessedKey()));
    }

    public OpenRaster open(ActiveRaster raster) {
        gdalInitializer.requireAvailable();
        return OpenRaster.open(raster.gdalPath());
    }

    public PixelValueDto pixelValues(String datasetName, double lon, double lat) {
        GeometryUtils.validateLonLat(lon, lat);
        ActiveRaster raster = resolve(datasetName);
        try (OpenRaster open = open(raster)) {
            double[] px = open.toPixel(lon, lat);
            Double[] values = open.valuesAt(lon, lat);
            return PixelValueDto.builder()
                    .datasetName(datasetName)
                    .versionNo(raster.versionNo())
                    .lon(lon)
                    .lat(lat)
                    .pixelX((int) Math.floor(px[0]))
                    .pixelY((int) Math.floor(px[1]))
                    .inside(values != null)
                    .values(values == null ? null : Arrays.asList(values))
                    .build();
        }
    }
}
