package com.minju.geogdalservice.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "raster_metadata")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RasterMetadata {

    @Id
    private Long versionId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "version_id")
    private DatasetVersion version;

    private int width;
    private int height;
    private int bandCount;

    @Column(nullable = false, length = 20)
    private String dataType;

    private Integer epsg;

    @Column(columnDefinition = "TEXT")
    private String srsWkt;

    // GDAL GeoTransform [originX, pixelW, rotX, originY, rotY, pixelH]
    @JdbcTypeCode(SqlTypes.ARRAY)
    private double[] geoTransform;

    @Column(name = "pixel_size_x")
    private Double pixelSizeX;

    @Column(name = "pixel_size_y")
    private Double pixelSizeY;

    // 미터 단위 대략 해상도 (지리 좌표계면 위도 보정)
    @Column(name = "resolution_m")
    private Double resolutionM;

    private Double nodataValue;
    private Double nodataRatio;
    private Double minValue;
    private Double maxValue;

    @Builder
    public RasterMetadata(DatasetVersion version, int width, int height, int bandCount, String dataType,
                          Integer epsg, String srsWkt, double[] geoTransform, Double pixelSizeX, Double pixelSizeY,
                          Double resolutionM, Double nodataValue, Double nodataRatio, Double minValue, Double maxValue) {
        this.version = version;
        this.width = width;
        this.height = height;
        this.bandCount = bandCount;
        this.dataType = dataType;
        this.epsg = epsg;
        this.srsWkt = srsWkt;
        this.geoTransform = geoTransform;
        this.pixelSizeX = pixelSizeX;
        this.pixelSizeY = pixelSizeY;
        this.resolutionM = resolutionM;
        this.nodataValue = nodataValue;
        this.nodataRatio = nodataRatio;
        this.minValue = minValue;
        this.maxValue = maxValue;
    }
}
