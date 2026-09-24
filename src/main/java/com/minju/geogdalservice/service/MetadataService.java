package com.minju.geogdalservice.service;

import com.minju.geogdalservice.dto.MetadataDto;
import com.minju.geogdalservice.entity.Metadata;
import com.minju.geogdalservice.repository.MetadataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MetadataService {

    private final MetadataRepository metadataRepository;

    public void saveMetadata(MetadataDto dto) {
        Metadata entity = Metadata.builder()
                .fileName(dto.getFileName())
                .width(dto.getWidth())
                .height(dto.getHeight())
                .bandCount(dto.getBandCount())
                .build();
        metadataRepository.save(entity);
    }

    //    public List<MetadataDto> searchByFileName(String fileName) {
//        return metadataRepository.findByFileName(fileName).stream()
//                .map(this::toDto)
//                .collect(Collectors.toList());
//    }
//
    private MetadataDto toDto(Metadata entity) {
        MetadataDto dto = new MetadataDto();
        dto.setFileName(entity.getFileName());
        dto.setWidth(entity.getWidth());
        dto.setHeight(entity.getHeight());
        dto.setBandCount(entity.getBandCount());
        return dto;
    }

    public List<MetadataDto> search(String fileName, Integer bandCount, Integer width) {
        return metadataRepository.search(fileName, bandCount, width).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

}