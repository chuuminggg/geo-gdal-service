package com.minju.geogdalservice.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.locationtech.jts.geom.Polygon;

import java.time.LocalDateTime;

@Entity
@Table(name = "dataset_version")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DatasetVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dataset_id")
    private Dataset dataset;

    @Column(nullable = false)
    private Integer versionNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VersionStatus status;

    @Column(nullable = false)
    private String originalFileName;

    // S3 원본 파일 키
    @Column(nullable = false, length = 512)
    private String rawKey;

    // S3 가공 결과 키 (래스터: COG)
    @Column(length = 512)
    private String processedKey;

    // SHA-256 (중복 업로드 검사)
    @Column(nullable = false, length = 64)
    private String checksum;

    @Column(nullable = false)
    private long fileSize;

    // 벡터 데이터의 피처 수
    private Integer featureCount;

    // 데이터 범위 (EPSG:4326)
    @Column(columnDefinition = "geometry(Polygon,4326)")
    private Polygon footprint;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private LocalDateTime publishedAt;

    @Builder
    public DatasetVersion(Dataset dataset, Integer versionNo, String originalFileName,
                          String rawKey, String checksum, long fileSize) {
        this.dataset = dataset;
        this.versionNo = versionNo;
        this.originalFileName = originalFileName;
        this.rawKey = rawKey;
        this.checksum = checksum;
        this.fileSize = fileSize;
        this.status = VersionStatus.UPLOADED;
    }

    /**
     * 상태 전이는 반드시 이 메서드를 통해서만 한다.
     * 허용되지 않은 전이(예: REJECTED -> PUBLISHED)는 IllegalStateException.
     */
    public void changeStatus(VersionStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new IllegalStateException(
                    "허용되지 않은 상태 전이입니다: %s -> %s (version id=%d)".formatted(status, target, id));
        }
        this.status = target;
        if (target == VersionStatus.PUBLISHED) {
            this.publishedAt = LocalDateTime.now();
        }
        if (target != VersionStatus.FAILED && target != VersionStatus.REJECTED) {
            this.errorMessage = null;
        }
    }

    public void recordError(String message) {
        this.errorMessage = message;
    }

    public void updateSpatialInfo(Polygon footprint, Integer featureCount) {
        this.footprint = footprint;
        this.featureCount = featureCount;
    }

    public void assignProcessedKey(String processedKey) {
        this.processedKey = processedKey;
    }
}
