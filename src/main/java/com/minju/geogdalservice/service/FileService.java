package com.minju.geogdalservice.service;

import com.minju.geogdalservice.dto.COGConversionDto;
import com.minju.geogdalservice.dto.FileUploadDto;
import com.minju.geogdalservice.dto.MetadataDto;
import com.minju.geogdalservice.entity.Metadata;
import com.minju.geogdalservice.repository.MetadataRepository;
import com.minju.geogdalservice.util.COGUtils;
import com.minju.geogdalservice.util.TempWorkspace;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FileService {

    private final S3Service s3Service;
    private final COGUtils cogUtils;
    private final MetadataRepository metadataRepository;

    @Value("${aws.s3.bucket.name}")
    private String bucketName;

    private static final String ORIGINAL_PREFIX = "original/";
    private static final String COG_PREFIX = "cog/";
    private static final String TIFF_CONTENT_TYPE = "image/tiff";
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".tif", ".tiff");

    public FileUploadDto uploadFile(MultipartFile file) {
        // 파일 검증
        validateFile(file);

        // 고유한 파일 키 생성
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String uniqueId = UUID.randomUUID().toString().substring(0, 8);
        String fileExtension = getFileExtension(file.getOriginalFilename());
        String s3Key = ORIGINAL_PREFIX + timestamp + "_" + uniqueId + fileExtension;

        try (TempWorkspace workspace = TempWorkspace.create("upload-")) {
            // 업로드 파일을 디스크에 한 번만 저장한 뒤 GDAL 검증과 S3 업로드에 재사용
            Path localFile = workspace.resolve("original" + fileExtension);
            file.transferTo(localFile);

            // GDAL로 열리는지 확인하면서 메타데이터 추출 (열리지 않으면 업로드하지 않음)
            MetadataDto metadata = cogUtils.extractMetadata(localFile, file.getOriginalFilename());

            // S3에 원본 파일 업로드
            s3Service.upload(bucketName, s3Key, localFile, TIFF_CONTENT_TYPE);

            // 데이터베이스에 메타데이터 저장
            saveMetadata(metadata, s3Key);

            return FileUploadDto.builder()
                    .fileName(metadata.getFileName())
                    .originalFileName(file.getOriginalFilename())
                    .fileSize(file.getSize())
                    .contentType(file.getContentType())
                    .s3Key(s3Key)
                    .s3Bucket(bucketName)
                    .build();

        } catch (IOException e) {
            throw new UncheckedIOException("파일 업로드 중 오류가 발생했습니다.", e);
        }
    }

    public COGConversionDto convertToCOG(String originalFileKey) {
        if (!originalFileKey.startsWith(ORIGINAL_PREFIX) || !s3Service.exists(bucketName, originalFileKey)) {
            throw new IllegalArgumentException("원본 파일을 찾을 수 없습니다: " + originalFileKey);
        }

        try (TempWorkspace workspace = TempWorkspace.create("cog-")) {
            // S3에서 원본 파일 다운로드
            Path original = workspace.resolve("original.tif");
            s3Service.download(bucketName, originalFileKey, original);

            // COG 변환
            Path cog = workspace.resolve("cog.tif");
            cogUtils.transformToCOG(original, cog);

            // COG 파일 키 생성 (original/xxx.tif -> cog/xxx_cog.tif)
            String baseName = originalFileKey.substring(ORIGINAL_PREFIX.length());
            String cogFileKey = COG_PREFIX + baseName.substring(0, baseName.lastIndexOf('.')) + "_cog.tif";

            // S3에 COG 파일 업로드
            s3Service.upload(bucketName, cogFileKey, cog, TIFF_CONTENT_TYPE);

            return COGConversionDto.builder()
                    .originalFileKey(originalFileKey)
                    .cogFileKey(cogFileKey)
                    .s3Bucket(bucketName)
                    .success(true)
                    .message("COG 변환이 성공적으로 완료되었습니다.")
                    .originalFileSize(Files.size(original))
                    .cogFileSize(Files.size(cog))
                    .build();

        } catch (IOException e) {
            throw new UncheckedIOException("COG 변환 중 오류가 발생했습니다.", e);
        }
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("업로드할 파일이 없습니다.");
        }

        // GeoTIFF는 브라우저/클라이언트에 따라 application/octet-stream 으로 오는 경우가 많아
        // Content-Type 대신 확장자로 1차 검증하고, 실제 포맷은 GDAL Open 으로 2차 검증한다.
        String extension = getFileExtension(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("지원되지 않는 파일 형식입니다. GeoTIFF(.tif, .tiff) 파일을 업로드해주세요.");
        }
    }

    private String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf(".")).toLowerCase(Locale.ROOT);
    }

    private void saveMetadata(MetadataDto metadata, String s3Key) {
        Metadata entity = Metadata.builder()
                .fileName(metadata.getFileName())
                .width(metadata.getWidth())
                .height(metadata.getHeight())
                .bandCount(metadata.getBandCount())
                .uploadedPath(s3Key)
                .build();

        metadataRepository.save(entity);
    }
}
