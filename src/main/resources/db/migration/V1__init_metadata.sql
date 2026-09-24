CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE metadata
(
    id            BIGSERIAL PRIMARY KEY,
    file_name     VARCHAR(255),
    width         INTEGER NOT NULL,
    height        INTEGER NOT NULL,
    band_count    INTEGER NOT NULL,
    uploaded_path VARCHAR(512)
);
