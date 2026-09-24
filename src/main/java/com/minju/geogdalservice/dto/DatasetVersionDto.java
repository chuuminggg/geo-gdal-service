package com.minju.geogdalservice.dto;

import com.minju.geogdalservice.entity.DatasetVersion;
import com.minju.geogdalservice.entity.VersionStatus;
import com.minju.geogdalservice.gis.GeoJsonMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DatasetVersionDto {
    private Long id;
    private String datasetName;
    private Integer versionNo;
    private VersionStatus status;
    private boolean active;
    private String originalFileName;
    private long fileSize;
    private String checksum;
    private String rawKey;
    private String processedKey;
    private Integer featureCount;
    private Map<String, Object> footprint;   // GeoJSON Polygon
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime publishedAt;
    private RasterMetadataDto raster;

    public static DatasetVersionDto from(DatasetVersion v, RasterMetadataDto raster) {
        var activeVersion = v.getDataset().getActiveVersion();
        return DatasetVersionDto.builder()
                .id(v.getId())
                .datasetName(v.getDataset().getName())
                .versionNo(v.getVersionNo())
                .status(v.getStatus())
                .active(activeVersion != null && activeVersion.getId().equals(v.getId()))
                .originalFileName(v.getOriginalFileName())
                .fileSize(v.getFileSize())
                .checksum(v.getChecksum())
                .rawKey(v.getRawKey())
                .processedKey(v.getProcessedKey())
                .featureCount(v.getFeatureCount())
                .footprint(GeoJsonMapper.toGeoJson(v.getFootprint()))
                .errorMessage(v.getErrorMessage())
                .createdAt(v.getCreatedAt())
                .publishedAt(v.getPublishedAt())
                .raster(raster)
                .build();
    }
}
