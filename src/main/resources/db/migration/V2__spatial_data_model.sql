-- =========================================================
-- 공간정보 데이터 모델
--   dataset          : 논리적 데이터셋 (예: seoul-dem, gangnam-road-network)
--   dataset_version  : 업로드 단위 버전. 수집→검수→가공→배포 상태를 가진다
--   raster_metadata  : 래스터 버전의 GDAL 메타데이터 (1:1)
--   road_node/link   : 경로탐색용 도로 네트워크 (버전별 스냅샷)
--   poi              : 관심지점 (버전별 스냅샷)
-- 좌표계는 모두 EPSG:4326(WGS84)로 정규화해서 저장한다.
-- =========================================================

CREATE TABLE dataset
(
    id                BIGSERIAL PRIMARY KEY,
    name              VARCHAR(100) NOT NULL UNIQUE,
    type              VARCHAR(20)  NOT NULL,
    description       VARCHAR(500),
    active_version_id BIGINT,
    created_at        TIMESTAMP    NOT NULL,
    updated_at        TIMESTAMP    NOT NULL
);

CREATE TABLE dataset_version
(
    id                 BIGSERIAL PRIMARY KEY,
    dataset_id         BIGINT       NOT NULL REFERENCES dataset (id),
    version_no         INTEGER      NOT NULL,
    status             VARCHAR(20)  NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    raw_key            VARCHAR(512) NOT NULL,
    processed_key      VARCHAR(512),
    checksum           VARCHAR(64)  NOT NULL,
    file_size          BIGINT       NOT NULL,
    feature_count      INTEGER,
    footprint          geometry(Polygon, 4326),
    error_message      TEXT,
    created_at         TIMESTAMP    NOT NULL,
    updated_at         TIMESTAMP    NOT NULL,
    published_at       TIMESTAMP,
    CONSTRAINT uk_dataset_version UNIQUE (dataset_id, version_no)
);

ALTER TABLE dataset
    ADD CONSTRAINT fk_dataset_active_version FOREIGN KEY (active_version_id) REFERENCES dataset_version (id);

-- 중복 업로드(동일 체크섬) 검사용
CREATE INDEX idx_dataset_version_checksum ON dataset_version (dataset_id, checksum);
-- "이 영역을 덮는 데이터" 검색용 공간 인덱스
CREATE INDEX idx_dataset_version_footprint ON dataset_version USING GIST (footprint);

CREATE TABLE raster_metadata
(
    version_id    BIGINT PRIMARY KEY REFERENCES dataset_version (id) ON DELETE CASCADE,
    width         INTEGER     NOT NULL,
    height        INTEGER     NOT NULL,
    band_count    INTEGER     NOT NULL,
    data_type     VARCHAR(20) NOT NULL,
    epsg          INTEGER,
    srs_wkt       TEXT,
    geo_transform DOUBLE PRECISION[],
    pixel_size_x  DOUBLE PRECISION,
    pixel_size_y  DOUBLE PRECISION,
    resolution_m  DOUBLE PRECISION,
    nodata_value  DOUBLE PRECISION,
    nodata_ratio  DOUBLE PRECISION,
    min_value     DOUBLE PRECISION,
    max_value     DOUBLE PRECISION
);

-- ---------------------------------------------------------
-- 도로 네트워크: 노드는 (버전, 순번) 복합키로 두어 대량 적재 시
-- ID 조회 왕복 없이 링크가 노드를 바로 참조할 수 있게 한다.
-- ---------------------------------------------------------
CREATE TABLE road_node
(
    version_id BIGINT                 NOT NULL REFERENCES dataset_version (id) ON DELETE CASCADE,
    node_seq   INTEGER                NOT NULL,
    geom       geometry(Point, 4326) NOT NULL,
    PRIMARY KEY (version_id, node_seq)
);
CREATE INDEX idx_road_node_geom ON road_node USING GIST (geom);

CREATE TABLE road_link
(
    id            BIGSERIAL PRIMARY KEY,
    version_id    BIGINT                      NOT NULL,
    source_id     VARCHAR(64),
    from_node_seq INTEGER                     NOT NULL,
    to_node_seq   INTEGER                     NOT NULL,
    road_class    VARCHAR(30),
    road_name     VARCHAR(200),
    oneway        BOOLEAN                     NOT NULL DEFAULT FALSE,
    max_speed_kph INTEGER,
    length_m      DOUBLE PRECISION            NOT NULL,
    geom          geometry(LineString, 4326) NOT NULL,
    FOREIGN KEY (version_id, from_node_seq) REFERENCES road_node (version_id, node_seq) ON DELETE CASCADE,
    FOREIGN KEY (version_id, to_node_seq) REFERENCES road_node (version_id, node_seq) ON DELETE CASCADE
);
CREATE INDEX idx_road_link_version ON road_link (version_id);
CREATE INDEX idx_road_link_geom ON road_link USING GIST (geom);

CREATE TABLE poi
(
    id         BIGSERIAL PRIMARY KEY,
    version_id BIGINT                 NOT NULL REFERENCES dataset_version (id) ON DELETE CASCADE,
    source_id  VARCHAR(64),
    name       VARCHAR(200),
    category   VARCHAR(50),
    address    VARCHAR(300),
    geom       geometry(Point, 4326) NOT NULL
);
CREATE INDEX idx_poi_version_category ON poi (version_id, category);
-- KNN(<->) 검색용 geometry 인덱스
CREATE INDEX idx_poi_geom ON poi USING GIST (geom);
-- 미터 단위 반경 검색(ST_DWithin geography)용 인덱스.
-- geom::geography 로 캐스팅하면 geometry 인덱스를 쓰지 못하므로 표현식 인덱스를 별도로 둔다.
CREATE INDEX idx_poi_geog ON poi USING GIST ((geom::geography));
