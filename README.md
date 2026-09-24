# 🌍 geo-gdal-service

GDAL 기반 **공간정보 데이터 처리 백엔드**입니다.
래스터(GeoTIFF/DEM)·도로 네트워크·POI 데이터를 **수집 → 검수 → 가공 → 배포**하는 파이프라인과,
배포된 데이터를 이용한 **공간 조회(고도·픽셀·좌표계·반경 검색) / 경로탐색(Dijkstra, A\*)** API를 제공합니다.

---
## 🎯 프로젝트 기간

- MVP 개발 기간 : 2025.05.08 ~
- 프로젝트 인원 : 1인 (개인)

---

## 📌 주요 기능

| 영역 | 기능 |
|---|---|
| **데이터 모델링** | 데이터셋/버전 모델, PostGIS geometry + GiST 인덱스, 래스터 메타데이터(EPSG, footprint, 해상도, NoData 비율) |
| **수집** | 버전 업로드, SHA-256 체크섬, S3 `raw/` 보관 |
| **검수** | 규칙 엔진 (좌표계·지리참조·서비스 영역·NoData·해상도 / geometry 타입·유효성·도로망 연결성·속성 누락 / 중복 파일), 검수 리포트 |
| **가공** | 래스터 → COG 변환, 도로망 노드-링크 토폴로지 구성 후 PostGIS 적재, POI 적재 |
| **배포** | 서비스 버전 전환, 롤백, 상태 변경 이력, 실패 단계부터 재시도, 서버 재시작 시 중단 작업 복구 |
| **공간 조회** | 위경도 → 픽셀 값, DEM 고도(쌍선형 보간)·경로 고도 프로파일, 좌표계 변환, POI 반경/KNN 검색, bbox 검색 |
| **경로탐색** | Dijkstra / A\*, 차량·보행·자전거 비용 모델, DEM 경사 반영, 일방통행, 배포 버전 즉시 반영 |

---

## 🔧 기술 스택

- Java 17, Spring Boot 3.4 (Web, Data JPA, Validation)
- PostgreSQL 16 + PostGIS 3.4, Hibernate Spatial(JTS), Flyway
- GDAL 3.6 (Java 바인딩) - GeoTIFF/COG, OGR 벡터(GeoJSON/Shapefile/GPKG), PROJ 좌표계 변환, `/vsis3/`
- AWS S3 (로컬: LocalStack)
- 테스트: JUnit 5, Testcontainers(PostGIS, LocalStack), GDAL Docker 이미지

---

## 🏗 아키텍처

```
                 ┌──────────────────────────── 파이프라인 (비동기, pipeline-* 스레드) ───────────────────────────┐
 업로드 ──▶ IngestService ──▶ S3 raw/ ──▶ PipelineRunner ──▶ 검수(ValidationRule*) ──▶ 가공(DatasetProcessor) ──▶ 배포(PublishService)
  (API)      SHA-256, 버전 채번           (AFTER_COMMIT)       REJECTED / VALIDATED      RASTER: COG → S3 processed/     active 버전 전환
                                                                                         ROAD : 토폴로지 → road_node/link  이전 버전 ARCHIVED
                                                                                         POI  : poi                        DatasetPublished 이벤트
                 └──────────────────────────────────────────────────────────────────────────────────────────────────────┘
                                                                                                          │
 조회 API ◀── RasterQueryService / ElevationService ◀── GDAL /vsis3/ (COG Range 읽기)                       │
          ◀── PoiService ◀── PostGIS (ST_DWithin, <-> KNN)                                                 │
          ◀── RouteService ◀── RoadGraphProvider (버전별 그래프 캐시) ◀────────── 그래프 무효화/미리 로드 ◀────┘
```

### 버전 상태 머신

```
UPLOADED → VALIDATING → VALIDATED → PROCESSING → PROCESSED → PUBLISHED ⇄ ARCHIVED (롤백)
                      ↘ REJECTED (검수 불합격)
VALIDATING / PROCESSING 중 시스템 오류 → FAILED → (재시도) 실패한 단계부터 재개
```
허용되지 않은 전이(예: `REJECTED → PUBLISHED`)는 엔티티에서 막고 `409 Conflict`로 응답합니다. 모든 전이는 `version_status_history`에 기록됩니다.

### 데이터 모델 (ERD 요약)

```
dataset (name UK, type, active_version_id FK)
  └─< dataset_version (version_no, status, raw_key, processed_key, checksum, footprint geometry(Polygon,4326) GiST)
        ├── raster_metadata (1:1, epsg, geo_transform[], resolution_m, nodata_ratio, min/max)
        ├─< road_node (PK: version_id + node_seq, geom Point GiST)
        ├─< road_link (from/to_node_seq FK, road_class, oneway, max_speed_kph, length_m, geom LineString GiST)
        ├─< poi (name, category, address, geom Point GiST + geography 표현식 GiST)
        ├─< validation_result (rule_code, severity, passed, message)
        ├─< processing_job (status, stage, attempt, auto_publish)
        └─< version_status_history (from → to, reason)
```

---

## 📡 API

| Method | Path | 설명 |
|---|---|---|
| POST | `/api/datasets` | 데이터셋 생성 `{"name":"seoul-dem","type":"RASTER"}` (RASTER / ROAD_NETWORK / POI) |
| POST | `/api/datasets/{name}/versions` | 새 버전 업로드 (multipart `file`, `autoPublish`) → jobId |
| GET | `/api/jobs/{jobId}` | 파이프라인 작업 상태 |
| POST | `/api/jobs/{jobId}/retry` | 실패한 작업 재시도 |
| GET | `/api/datasets/{name}/versions[/{v}]` | 버전 목록/상세 (래스터 메타데이터, footprint GeoJSON) |
| GET | `/api/datasets/{name}/versions/{v}/validation` | 검수 리포트 |
| GET | `/api/datasets/{name}/versions/{v}/history` | 상태 변경 이력 |
| POST | `/api/datasets/{name}/versions/{v}/publish` | 배포 (ARCHIVED 버전 지정 시 롤백) |
| POST | `/api/datasets/{name}/rollback` | 직전 배포 버전으로 롤백 |
| GET | `/api/datasets/search?minLon&minLat&maxLon&maxLat&type` | bbox 와 겹치는 배포 데이터 |
| GET | `/api/rasters/{name}/value?lon&lat` | 위경도 기반 픽셀 값 (밴드별) |
| GET | `/api/elevation?lon&lat&dataset` | 고도 (쌍선형 보간) |
| POST | `/api/elevation/batch` | 다건 고도 |
| POST | `/api/elevation/profile` | 경로 고도 프로파일 (누적 오르막/내리막) |
| GET/POST | `/api/coordinates/transform` | 좌표계 변환 (예: 4326 → 5179) |
| GET | `/api/pois/nearby?lon&lat&radius&category` | 반경 내 POI (거리순) |
| GET | `/api/pois/nearest?lon&lat&k` | 가장 가까운 K개 POI |
| GET | `/api/routes?fromLon&fromLat&toLon&toLat&mode&algorithm` | 경로탐색 (CAR / WALK / BIKE, ASTAR / DIJKSTRA) |
| GET | `/api/routes/compare?...` | Dijkstra vs A\* 비교 (최적성, 탐색 노드 수) |

---

## 💡 설계 포인트

- **좌표계 정규화**: 모든 geometry 는 EPSG:4326 으로 저장하고, 래스터는 원본 좌표계를 유지한 채 조회 시점에 변환합니다. GDAL 3 의 축 순서 문제(EPSG:4326 = 위도, 경도)는 `OAMS_TRADITIONAL_GIS_ORDER`로 통일했습니다.
- **대량 적재는 JDBC batch, 메타데이터는 JPA**: 도로 링크·POI 는 `JdbcTemplate.batchUpdate` + `reWriteBatchedInserts`로 적재하고, 노드 키를 (버전 ID, 로컬 순번 `node_seq`)으로 두어 ID 조회 왕복 없이 링크가 노드를 참조합니다.
- **긴 작업은 트랜잭션 밖에서**: GDAL 변환·S3 전송 중 DB 커넥션을 잡지 않도록 상태 변경만 짧은 트랜잭션으로 분리했습니다. 파이프라인은 작업 생성이 커밋된 뒤(`@TransactionalEventListener(AFTER_COMMIT)`) 전용 스레드 풀에서 시작합니다.
- **멱등 재시도**: 가공 단계는 해당 버전의 적재분을 지우고 한 트랜잭션으로 다시 적재하므로 몇 번 재시도해도 결과가 같습니다.
- **검수 규칙 확장**: `ValidationRule<T>` 구현체를 `@Component`로 추가하면 자동으로 실행됩니다. 규칙은 GDAL 과 분리된 값 객체(`RasterInfo`, `VectorData`)를 받아 네이티브 라이브러리 없이 단위 테스트합니다.
- **COG + `/vsis3/`**: 조회 시 파일 전체를 내려받지 않고 GDAL 이 필요한 타일만 HTTP Range 로 읽습니다.
- **경로 그래프**: CSR 배열 구조의 불변 그래프라 잠금 없이 동시 탐색하고, 캐시 키를 버전 ID 로 두어 배포/롤백 즉시(다중 인스턴스에서도) 올바른 도로망을 사용합니다.
- **A\* 최적성**: 휴리스틱 = 직선거리 / 수단별 최대속도 → admissible·consistent 하므로 Dijkstra 와 같은 최적해를 보장합니다. (테스트에서 Bellman-Ford·Dijkstra 와 대조)

---

## 📊 측정 결과 (테스트 코드 기준)

| 항목 | 결과 |
|---|---|
| 격자 공간 인덱스 vs 전수 탐색 (점 10만 개, 최근접 질의) | 약 **570배** 빠름 (1,000 질의 측정: 17ms vs 9.7s) |
| A\* vs Dijkstra (40×40 격자, 600 질의) | 확정 노드 수 **62.7%**, 비용은 동일 |
| A\* vs Dijkstra (100×100 격자, 서버 측 400쌍) | 확정 노드 수 **47%**, 탐색 시간 p50 1.43 → 0.98ms |
| 래스터 핸들 풀 도입 (고도/픽셀 조회, 10 VU) | p95 **136 → 8.5ms (-94%)**, 3회 반복 범위가 겹치지 않음 |

---

## 🚀 실행 방법

```bash
# 1. PostGIS + LocalStack(S3, geogdal-files 버킷 자동 생성)
docker compose up -d

# 2. GDAL 3.6 네이티브 라이브러리 설치 후 java.library.path 지정하여 실행
./gradlew bootRun
```
> GDAL 이 로드되지 않아도 애플리케이션은 기동되며, GDAL 이 필요한 API 는 `503`을 응답합니다.
> [GDAL 다운로드 링크](https://www.gisinternals.com/query.html?content=filelist&file=release-1930-x64-gdal-3-6-3-mapserver-8-0-0.zip)

주요 설정 (`application.properties` / 환경 변수)

| 설정 | 설명 |
|---|---|
| `ELEVATION_DATASET` | 고도 조회·경로 경사 반영에 쓸 DEM 데이터셋 이름 |
| `ROUTING_DATASET` | 기본 도로 네트워크 데이터셋 이름 |
| `geo.routing.max-snap-distance-m` | 좌표 → 도로 스냅 허용 거리 (기본 500m) |

### 테스트

```bash
# 단위 + PostGIS 통합 테스트 (GDAL 필요 테스트는 자동 skip)
./gradlew test

# 전체 테스트 (GDAL + PostGIS + LocalStack E2E 포함)
docker build -t geogdal-gdal docker/gdal
docker run --rm -v "$PWD":/src:ro -v geogdal-gradle:/root/.gradle \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
  geogdal-gdal bash /src/docker/gdal/run-tests.sh
```

### 시스템 테스트 (E2E 47개 시나리오 + 성능)

실제 배포 환경(App + PostGIS + LocalStack)에 HTTP 로 요청하는 시나리오 테스트와 k6 부하 테스트.
시나리오 목록, 기대값, 실행 결과, 발견된 결함은 [docs/TEST_PLAN.md](docs/TEST_PLAN.md) 참고.

```bash
bash scripts/run-system-tests.sh          # 스택 기동 + 테스트 데이터 생성 + E2E
bash scripts/run-system-tests.sh --perf   # + 성능 테스트 (k6 필요)
```

---

## 🚨 트러블 슈팅

- **GDAL 네이티브 로딩 실패 시 앱 전체가 기동되지 않음** → `static { gdal.AllRegister(); }` 대신 `GdalInitializer`에서 로딩 실패를 기록하고, GDAL 이 필요한 시점에 503 으로 응답하도록 분리
- **GeoTIFF 업로드가 Content-Type 검증에서 거부됨** → 클라이언트에 따라 `application/octet-stream`으로 오므로 확장자 + GDAL Open 결과로 검증
- **GDAL OSR 오류가 반환 코드가 아니라 `RuntimeException("OGR Error")`로 던져짐** → 잘못된 EPSG 요청이 500 이 되던 문제를 400 으로 처리
- **경로 고도 프로파일에서 꺾이는 지점의 고도를 놓침** → 일정 간격 샘플에 폴리라인 정점을 항상 포함
- **DEM 재배포 직후 경로 요청이 이전 고도 그래프를 사용** → 캐시 무효화는 커밋 직후 동기로, 그래프 미리 로드만 비동기로 분리
- **고도/픽셀 조회 p95 136ms** → 요청마다 S3 의 COG 를 여는 비용이 대부분. 버전별 열린 핸들 풀로 재사용해 8.5ms
- **성능 측정이 좋게 나왔는데 사실은 503** → 장애 시나리오가 S3 를 초기화한 뒤 측정해 오류 응답(빠름)이 섞였음. 시나리오별 오류율 검사와 데이터 가용성 사전 확인 추가
- **GDAL `/vsis3/` 설정이 프로세스 전역** → 테스트 컨텍스트가 여러 개일 때 LocalStack 엔드포인트가 덮어써지는 문제를 컨테이너 공유로 해결

---

## 📌 향후 개선사항

- 벡터 타일(MVT, `ST_AsMVT`) 서빙 API
- 성능 측정 자동화 (k6 부하 테스트, 대용량 파일 메모리 사용량 비교)
- 캐시(Caffeine/Redis), Actuator + Prometheus 모니터링
- 경로탐색: 양방향 탐색, Contraction Hierarchies, 회전 제한, pgRouting 결과와 교차 검증
- 대중교통·주소 데이터 타입 추가 (`DatasetProcessor` 구현체 추가로 확장)
