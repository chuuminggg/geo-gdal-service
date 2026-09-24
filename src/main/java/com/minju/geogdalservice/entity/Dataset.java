package com.minju.geogdalservice.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "dataset")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Dataset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DatasetType type;

    @Column(length = 500)
    private String description;

    // 현재 서비스에 배포(PUBLISHED)되어 있는 버전
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "active_version_id")
    private DatasetVersion activeVersion;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public Dataset(String name, DatasetType type, String description) {
        this.name = name;
        this.type = type;
        this.description = description;
    }

    public void changeActiveVersion(DatasetVersion version) {
        this.activeVersion = version;
    }
}
