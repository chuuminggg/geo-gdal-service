package com.minju.geogdalservice.service;

import com.minju.geogdalservice.dto.MetadataDto;
import com.minju.geogdalservice.entity.Metadata;
import com.minju.geogdalservice.repository.MetadataRepository;
import com.minju.geogdalservice.util.GdalRasterSupport;
import lombok.RequiredArgsConstructor;
import org.gdal.gdal.Dataset;
import org.gdal.gdal.gdal;
import org.gdal.osr.SpatialReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MetadataService {

    private final MetadataRepository metadataRepository;
    private final GdalRasterSupport gdalRasterSupport;

    public void saveMetadata(MetadataDto dto) {
        Metadata entity = Metadata.builder()
                .fileName(dto.getFileName())
                .width(dto.getWidth())
                .height(dto.getHeight())
                .bandCount(dto.getBandCount())
                .build();
        metadataRepository.save(entity);
    }

    // GeoTIFF 파일에서 GDAL로 메타데이터를 추출하고 저장 (같은 파일명은 갱신)
    @Transactional
    public MetadataDto extractAndSave(String fileName) {
        return gdalRasterSupport.withDataset(fileName, (path, dataset) -> {
            Metadata entity = metadataRepository.findByFileName(fileName).stream()
                    .findFirst()
                    .orElseGet(Metadata::new);
            fill(entity, fileName, path, dataset);
            return toDto(metadataRepository.save(entity));
        });
    }

    private void fill(Metadata entity, String fileName, Path path, Dataset dataset) {
        int width = dataset.GetRasterXSize();
        int height = dataset.GetRasterYSize();
        double[] gt = dataset.GetGeoTransform();

        entity.setFileName(fileName);
        entity.setUploadedPath(path.toString());
        entity.setWidth(width);
        entity.setHeight(height);
        entity.setBandCount(dataset.GetRasterCount());
        entity.setDriver(dataset.GetDriver().getShortName());
        entity.setDataType(dataset.GetRasterCount() > 0
                ? gdal.GetDataTypeName(dataset.GetRasterBand(1).getDataType())
                : null);
        entity.setCrs(describeCrs(dataset.GetSpatialRef()));
        entity.setPixelSizeX(Math.abs(gt[1]));
        entity.setPixelSizeY(Math.abs(gt[5]));

        // 네 모서리 좌표로 범위 계산 (회전 계수가 있어도 대응)
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        for (int[] corner : new int[][]{{0, 0}, {width, 0}, {0, height}, {width, height}}) {
            double x = gt[0] + corner[0] * gt[1] + corner[1] * gt[2];
            double y = gt[3] + corner[0] * gt[4] + corner[1] * gt[5];
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
        }
        entity.setMinX(minX);
        entity.setMinY(minY);
        entity.setMaxX(maxX);
        entity.setMaxY(maxY);
    }

    // EPSG 코드가 있으면 "EPSG:xxxx", 없으면 좌표계 이름
    private String describeCrs(SpatialReference srs) {
        if (srs == null) {
            return null;
        }
        String authorityName = srs.GetAuthorityName(null);
        String authorityCode = srs.GetAuthorityCode(null);
        if (authorityName == null || authorityCode == null) {
            try {
                srs.AutoIdentifyEPSG();
                authorityName = srs.GetAuthorityName(null);
                authorityCode = srs.GetAuthorityCode(null);
            } catch (RuntimeException ignored) {
                // EPSG로 식별되지 않는 좌표계
            }
        }
        if (authorityName != null && authorityCode != null) {
            return authorityName + ":" + authorityCode;
        }
        return srs.GetName();
    }

    private MetadataDto toDto(Metadata entity) {
        MetadataDto dto = new MetadataDto();
        dto.setFileName(entity.getFileName());
        dto.setWidth(entity.getWidth());
        dto.setHeight(entity.getHeight());
        dto.setBandCount(entity.getBandCount());
        dto.setDriver(entity.getDriver());
        dto.setDataType(entity.getDataType());
        dto.setCrs(entity.getCrs());
        dto.setPixelSizeX(entity.getPixelSizeX());
        dto.setPixelSizeY(entity.getPixelSizeY());
        dto.setMinX(entity.getMinX());
        dto.setMinY(entity.getMinY());
        dto.setMaxX(entity.getMaxX());
        dto.setMaxY(entity.getMaxY());
        return dto;
    }

    public List<MetadataDto> search(String fileName, Integer bandCount, Integer width) {
        return metadataRepository.search(fileName, bandCount, width).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

}
