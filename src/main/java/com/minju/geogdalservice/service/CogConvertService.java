package com.minju.geogdalservice.service;

import com.minju.geogdalservice.common.exception.GeoServiceException;
import com.minju.geogdalservice.dto.CogConvertDto;
import com.minju.geogdalservice.util.GdalRasterSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.gdal.gdal.Dataset;
import org.gdal.gdal.TranslateOptions;
import org.gdal.gdal.gdal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;
import java.util.Vector;

@Slf4j
@Service
@RequiredArgsConstructor
public class CogConvertService {

    private static final String DEFAULT_COMPRESSION = "DEFLATE";
    private static final int DEFAULT_BLOCK_SIZE = 512;

    private final GdalRasterSupport gdalRasterSupport;
    private final RasterStorageService rasterStorageService;

    // GeoTIFF → COG(Cloud Optimized GeoTIFF) 변환 (gdal_translate -of COG)
    public CogConvertDto.Response convert(CogConvertDto.Request request) {
        String compression = request.getCompression() != null
                ? request.getCompression().toUpperCase(Locale.ROOT)
                : DEFAULT_COMPRESSION;
        int blockSize = request.getBlockSize() != null ? request.getBlockSize() : DEFAULT_BLOCK_SIZE;
        String outputFileName = request.getOutputFileName() != null && !request.getOutputFileName().isBlank()
                ? request.getOutputFileName()
                : defaultOutputFileName(request.getFileName());

        if (outputFileName.equals(request.getFileName())) {
            throw GeoServiceException.badRequest("outputFileName must be different from fileName");
        }
        Path outputPath = rasterStorageService.resolveForWrite(outputFileName);
        if (Files.exists(outputPath) && !request.isOverwrite()) {
            throw new GeoServiceException(HttpStatus.CONFLICT,
                    "Output file already exists: " + outputFileName + " (set overwrite=true to replace)");
        }

        return gdalRasterSupport.withDataset(request.getFileName(), (sourcePath, source) -> {
            long start = System.currentTimeMillis();
            translateToCog(source, outputPath, compression, blockSize);
            long elapsed = System.currentTimeMillis() - start;

            CogConvertDto.Response.ResponseBuilder response = CogConvertDto.Response.builder()
                    .sourceFileName(request.getFileName())
                    .outputFileName(outputFileName)
                    .compression(compression)
                    .blockSize(blockSize)
                    .sourceSize(sizeOf(sourcePath))
                    .outputSize(sizeOf(outputPath))
                    .elapsedMillis(elapsed);

            // 결과 파일을 다시 열어 COG 레이아웃 확인
            return gdalRasterSupport.withDataset(outputFileName, (path, output) -> response
                    .layout(output.GetMetadataItem("LAYOUT", "IMAGE_STRUCTURE"))
                    .overviewCount(output.GetRasterCount() > 0 ? output.GetRasterBand(1).GetOverviewCount() : 0)
                    .build());
        });
    }

    private void translateToCog(Dataset source, Path outputPath, String compression, int blockSize) {
        Vector<String> options = new Vector<>();
        options.add("-of");
        options.add("COG");
        options.add("-co");
        options.add("COMPRESS=" + compression);
        options.add("-co");
        options.add("BLOCKSIZE=" + blockSize);
        options.add("-co");
        options.add("OVERVIEWS=AUTO");
        options.add("-co");
        options.add("NUM_THREADS=ALL_CPUS");

        // 기존 파일을 바로 덮어쓰지 않도록 임시 파일로 변환 후 교체
        Path tempPath = outputPath.resolveSibling(outputPath.getFileName() + "." + UUID.randomUUID() + ".tmp");
        TranslateOptions translateOptions = new TranslateOptions(options);
        Dataset result = null;
        try {
            result = gdal.Translate(tempPath.toString(), source, translateOptions);
            if (result == null) {
                throw new GeoServiceException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "COG conversion failed: " + gdal.GetLastErrorMsg());
            }
            result.delete();
            result = null;
            Files.move(tempPath, outputPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (GeoServiceException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new GeoServiceException(HttpStatus.UNPROCESSABLE_ENTITY, "COG conversion failed: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new GeoServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to write COG file: " + outputPath.getFileName(), e);
        } finally {
            if (result != null) {
                result.delete();
            }
            translateOptions.delete();
            deleteQuietly(tempPath);
        }
        log.info("COG conversion completed: {}", outputPath);
    }

    private String defaultOutputFileName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        String baseName = dot > 0 ? fileName.substring(0, dot) : fileName;
        return baseName + "_cog.tif";
    }

    private long sizeOf(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            return -1;
        }
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Failed to delete temp file: {}", path, e);
        }
    }
}
