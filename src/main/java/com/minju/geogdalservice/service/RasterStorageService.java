package com.minju.geogdalservice.service;

import com.minju.geogdalservice.common.exception.GeoServiceException;
import com.minju.geogdalservice.dto.RasterFileDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

// GeoTIFF 파일 저장소 (로컬 디렉터리)
@Slf4j
@Service
public class RasterStorageService {

    private static final List<String> ALLOWED_EXTENSIONS = List.of(".tif", ".tiff");

    private final Path baseDir;

    public RasterStorageService(@Value("${geo.storage.dir:./data/rasters}") String storageDir) {
        this.baseDir = Paths.get(storageDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(baseDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create storage directory: " + baseDir, e);
        }
        log.info("Raster storage directory: {}", baseDir);
    }

    // 업로드
    public RasterFileDto store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw GeoServiceException.badRequest("Uploaded file is empty");
        }
        Path target = resolveForWrite(file.getOriginalFilename());
        try (InputStream input = file.getInputStream()) {
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new GeoServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store file: " + target.getFileName(), e);
        }
        return toDto(target);
    }

    // 파일 목록
    public List<RasterFileDto> list() {
        try (Stream<Path> files = Files.list(baseDir)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> hasAllowedExtension(path.getFileName().toString()))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .map(this::toDto)
                    .toList();
        } catch (IOException e) {
            throw new GeoServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to list files", e);
        }
    }

    // 존재하는 파일 경로 조회
    public Path resolveExisting(String fileName) {
        Path path = resolve(fileName);
        if (!Files.isRegularFile(path)) {
            throw GeoServiceException.notFound("File not found: " + fileName);
        }
        return path;
    }

    // 새로 저장할 파일 경로 조회
    public Path resolveForWrite(String fileName) {
        return resolve(fileName);
    }

    public RasterFileDto toDto(Path path) {
        try {
            return RasterFileDto.builder()
                    .fileName(path.getFileName().toString())
                    .size(Files.size(path))
                    .lastModified(Files.getLastModifiedTime(path).toInstant())
                    .build();
        } catch (IOException e) {
            throw new GeoServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read file info: " + path.getFileName(), e);
        }
    }

    // 파일명 검증: 경로 구분자, 상위 경로 접근 차단
    private Path resolve(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw GeoServiceException.badRequest("fileName is required");
        }
        if (fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")) {
            throw GeoServiceException.badRequest("Invalid fileName: " + fileName);
        }
        if (!hasAllowedExtension(fileName)) {
            throw GeoServiceException.badRequest("Only GeoTIFF files are supported (" + String.join(", ", ALLOWED_EXTENSIONS) + ")");
        }
        Path path = baseDir.resolve(fileName).normalize();
        if (!baseDir.equals(path.getParent())) {
            throw GeoServiceException.badRequest("Invalid fileName: " + fileName);
        }
        return path;
    }

    private boolean hasAllowedExtension(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        return ALLOWED_EXTENSIONS.stream().anyMatch(lower::endsWith);
    }
}
