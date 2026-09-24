package com.minju.geogdalservice.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "metadata")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Metadata {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String fileName;
    private int width;
    private int height;
    private int bandCount;
    private String uploadedPath;
}