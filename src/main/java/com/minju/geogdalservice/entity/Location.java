package com.minju.geogdalservice.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "location", indexes = @Index(name = "idx_location_lat_lon", columnList = "latitude, longitude"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Location {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String category;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    private String description;
}
