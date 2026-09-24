package com.minju.geogdalservice.service;

import com.minju.geogdalservice.common.exception.NotFoundException;
import com.minju.geogdalservice.dto.DatasetCreateRequest;
import com.minju.geogdalservice.dto.DatasetDto;
import com.minju.geogdalservice.dto.DatasetVersionDto;
import com.minju.geogdalservice.dto.RasterMetadataDto;
import com.minju.geogdalservice.entity.Dataset;
import com.minju.geogdalservice.entity.DatasetType;
import com.minju.geogdalservice.entity.DatasetVersion;
import com.minju.geogdalservice.gis.GeometryUtils;
import com.minju.geogdalservice.repository.DatasetRepository;
import com.minju.geogdalservice.repository.DatasetVersionRepository;
import com.minju.geogdalservice.repository.RasterMetadataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DatasetService {

    private final DatasetRepository datasetRepository;
    private final DatasetVersionRepository versionRepository;
    private final RasterMetadataRepository rasterMetadataRepository;

    @Transactional
    public DatasetDto create(DatasetCreateRequest request) {
        if (datasetRepository.existsByName(request.getName())) {
            throw new IllegalStateException("이미 존재하는 데이터셋 이름입니다: " + request.getName());
        }
        Dataset dataset = datasetRepository.save(Dataset.builder()
                .name(request.getName())
                .type(request.getType())
                .description(request.getDescription())
                .build());
        return DatasetDto.from(dataset);
    }

    public List<DatasetDto> findAll() {
        return datasetRepository.findAllByOrderByIdAsc().stream()
                .map(DatasetDto::from)
                .toList();
    }

    public DatasetDto findByName(String name) {
        return DatasetDto.from(getDataset(name));
    }

    public List<DatasetVersionDto> findVersions(String name) {
        Dataset dataset = getDataset(name);
        return versionRepository.findByDatasetIdOrderByVersionNoDesc(dataset.getId()).stream()
                .map(this::toDto)
                .toList();
    }

    public DatasetVersionDto findVersion(String name, int versionNo) {
        return toDto(getVersion(name, versionNo));
    }

    // bbox와 겹치는 배포 중인 데이터 검색 (GiST 인덱스 사용)
    public List<DatasetVersionDto> searchByBbox(double minLon, double minLat, double maxLon, double maxLat,
                                                DatasetType type) {
        GeometryUtils.envelope(minLon, minLat, maxLon, maxLat); // 범위 검증
        return versionRepository.searchActiveIntersecting(minLon, minLat, maxLon, maxLat,
                        type == null ? null : type.name()).stream()
                .map(this::toDto)
                .toList();
    }

    public Dataset getDataset(String name) {
        return datasetRepository.findByName(name)
                .orElseThrow(() -> new NotFoundException("데이터셋을 찾을 수 없습니다: " + name));
    }

    public DatasetVersion getVersion(String name, int versionNo) {
        Dataset dataset = getDataset(name);
        return versionRepository.findByDatasetIdAndVersionNo(dataset.getId(), versionNo)
                .orElseThrow(() -> new NotFoundException("버전을 찾을 수 없습니다: %s v%d".formatted(name, versionNo)));
    }

    public DatasetVersionDto toDto(DatasetVersion version) {
        RasterMetadataDto raster = version.getDataset().getType() == DatasetType.RASTER
                ? rasterMetadataRepository.findById(version.getId()).map(RasterMetadataDto::from).orElse(null)
                : null;
        return DatasetVersionDto.from(version, raster);
    }
}
