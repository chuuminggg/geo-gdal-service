# 🌍 geo-gdal-service

GDAL 기반 **공간정보 데이터 처리 백엔드**입니다.

래스터(GeoTIFF/DEM)·도로 네트워크·POI 데이터를 **수집 → 검수 → 가공 → 배포**하는 파이프라인을 제공합니다.
배포된 데이터로 **공간 조회**(픽셀 값·고도·좌표계 변환·반경/KNN 검색)와 **경로탐색**(Dijkstra, A\*)도 할 수 있습니다.

---

## 🎯 프로젝트 개요

| 항목 | 내용 |
|---|---|
| 기간 | MVP 2025.05.08 ~ / 고도화 2026.09 |
| 인원 | 1인 (개인) |
| 목표 | 공간 데이터를 **버전 단위로 검수·가공·배포**하고, 배포된 데이터를 조회·경로탐색 서비스에 **안정적으로 제공**하는 백엔드 |

### 개발 이력

| 단계 | 내용 |
|---|---|
| v1 (2025.05 ~ 2026.09.09) | 로컬 파일 저장소 + H2 기반 GeoTIFF API: 메타데이터, 픽셀 값, 좌표 변환, 반경 검색, COG 변환, 고도 조회 |
| v2 (2026.09.24 ~) | **PostGIS + S3 기반 데이터셋 버전 파이프라인으로 재설계** (v1 API 는 새 구조로 대체) |
| | Phase 0: PostgreSQL/PostGIS 전환, 파일 스트리밍, GDAL 로딩 분리 |
| | Phase 1: 공간 데이터 모델링 (데이터셋/버전, GiST 인덱스, 래스터 메타데이터) |
| | Phase 2: 수집 → 검수 → 가공 → 배포 파이프라인 (규칙 엔진, 비동기 작업, 재시도·롤백) |
| | Phase 3: 공간 조회 API (`/vsis3/` COG 조회, 고도 프로파일, POI 반경·KNN) |
| | Phase 4: 경로탐색 (CSR 그래프, Dijkstra/A\*, 이동수단별 비용, DEM 경사) |
| | 시스템 테스트: E2E 47개 시나리오, 장애 주입, 성능 A/B → 결함 수정, 성능 개선 |

---

## 🏆 결과 요약

| 항목 | 결과 |
|---|---|
| 자동화 테스트 | JUnit **81개** (단위 53 + 통합 28), 시스템 E2E **47개** 시나리오 - 모두 통과 |
| 테스트로 찾아 수정한 결함 | 기능·안정성 **4건**, 성능 **1건**, 측정 방법 **3건** |
| 래스터 조회 성능 | 고도 p95 **135.9 → 8.5ms (-94%)**, 픽셀 p95 **145.9 → 7.8ms (-95%)** |
| 부하 | 30 동시 사용자, 약 **2,640 req/s**, 248,258건 중 오류 **0건** |
| 경로탐색 | A\* 가 Dijkstra 대비 탐색 노드 **43~47%**, 최적 비용 불일치 **0 / 600쌍** |
| 장애 대응 | 가공 중 서버 재시작 → 실패 단계부터 재시도 / S3 장애 시 **6.4초 내 503**, DB 기반 조회는 계속 동작 |
| 동시성 | 배포 전환 중 경로 요청 약 1,900건 동안 오류 **0건**, 동시 업로드 10건의 버전 번호 중복 **0건** |

> 상세: [테스트 계획](docs/TEST_PLAN.md) · [테스트 결과 비교 분석](docs/TEST_RESULT_ANALYSIS.md)

---

## 📌 주요 기능

| 영역 | 기능 |
|---|---|
| **데이터 모델링** | 데이터셋/버전 모델, PostGIS geometry + GiST 인덱스, 래스터 메타데이터 (EPSG, footprint, 해상도, NoData 비율) |
| **수집** | 버전 업로드, SHA-256 체크섬, S3 `raw/` 보관, 동시 업로드 시 버전 번호 행 잠금 |
| **검수** | 규칙 엔진 → 검수 리포트 저장. 래스터: 좌표계·지리참조·서비스 영역·NoData·해상도. 벡터: geometry 타입·유효성·도로망 연결성·속성 누락. 공통: 중복 파일 |
| **가공** | 래스터 → COG 변환 (S3 `processed/`), 도로망 노드-링크 토폴로지 구성 후 PostGIS 적재, POI 적재 |
| **배포** | 서비스 버전 전환, 롤백, 상태 변경 이력, 실패 단계부터 재시도, 서버 재시작 시 중단 작업 복구 |
| **공간 조회** | 위경도 → 픽셀 값, DEM 고도 (쌍선형 보간), 경로 고도 프로파일, 좌표계 변환, POI 반경/KNN 검색, bbox 검색 |
| **경로탐색** | Dijkstra / A\*, 차량·보행·자전거 비용 모델, DEM 경사 반영, 일방통행·자동차전용도로, 배포 버전 즉시 반영 |

지원 형식: GeoTIFF (`.tif`, `.tiff`) / GeoJSON, Shapefile(zip), GeoPackage. 입력 좌표계는 자동으로 WGS84 로 변환합니다.
도로 등급은 OSM `highway` 태그와 국가표준노드링크 `ROAD_RANK` 코드(101~108)를 모두 지원합니다.

---

## 🔧 기술 스택

| 구분 | 사용 기술 |
|---|---|
| Backend | Java 17, Spring Boot 3.4 (Web, Data JPA, Validation) |
| DB | PostgreSQL 16 + PostGIS 3.4, Hibernate Spatial (JTS), Flyway |
| 공간 처리 | GDAL 3.6 (Java 바인딩): GeoTIFF/COG, OGR 벡터, PROJ 좌표계 변환, `/vsis3/` 원격 읽기 |
| 스토리지 | AWS S3 (로컬: LocalStack) |
| 인프라 | Docker, Docker Compose |
| 테스트 | JUnit 5, Testcontainers (PostGIS, LocalStack), Python E2E 러너, k6 |

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
                              ActiveVersionResolver (데이터셋 → 서비스 버전 캐시) ◀──── 즉시 무효화 ◀──────┤
                                                                                                          │
 조회 API ◀── RasterQueryService / ElevationService ◀── RasterHandlePool ◀── GDAL /vsis3/ (COG Range 읽기)  │
          ◀── PoiService ◀── PostGIS (ST_DWithin, <-> KNN)                                                 │
          ◀── RouteService ◀── RoadGraphProvider (버전별 그래프 캐시) ◀────────── 그래프 무효화/미리 로드 ◀────┘
```

### 버전 상태 머신

```
UPLOADED → VALIDATING → VALIDATED → PROCESSING → PROCESSED → PUBLISHED ⇄ ARCHIVED (롤백)
                      ↘ REJECTED (검수 불합격)
VALIDATING / PROCESSING 중 시스템 오류 → FAILED → (재시도) 실패한 단계부터 재개
```
허용되지 않은 전이(예: `REJECTED → PUBLISHED`)는 엔티티에서 막고 `409 Conflict`로 응답합니다.
모든 전이는 `version_status_history`에 기록됩니다.

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
| POST | `/api/datasets/{name}/versions/{v}/publish` | 배포 (ARCHIVED 버전을 지정하면 롤백) |
| POST | `/api/datasets/{name}/rollback` | 직전 배포 버전으로 롤백 |
| GET | `/api/datasets/search?minLon&minLat&maxLon&maxLat&type` | bbox 와 겹치는 배포 데이터 |
| GET | `/api/rasters/{name}/value?lon&lat` | 위경도 기반 픽셀 값 (밴드별) |
| GET | `/api/elevation?lon&lat&dataset` | 고도 (쌍선형 보간) |
| POST | `/api/elevation/batch` | 다건 고도 (최대 1,000점) |
| POST | `/api/elevation/profile` | 경로 고도 프로파일 (누적 오르막/내리막) |
| GET / POST | `/api/coordinates/transform` | 좌표계 변환 (예: 4326 → 5179, 최대 10,000점) |
| GET | `/api/pois/nearby?lon&lat&radius&category` | 반경 내 POI (거리순) |
| GET | `/api/pois/nearest?lon&lat&k` | 가장 가까운 K개 POI |
| GET | `/api/routes?fromLon&fromLat&toLon&toLat&mode&algorithm` | 경로탐색 (CAR / WALK / BIKE, ASTAR / DIJKSTRA) |
| GET | `/api/routes/compare?...` | Dijkstra vs A\* 비교 (최적성, 탐색 노드 수) |

오류 응답: `400` 잘못된 입력 · `404` 없음/미배포 · `409` 상태 충돌 · `413` 파일 크기 초과 · `503` GDAL 미로딩/스토리지 장애

### 사용 예시

```bash
# 1) 도로망 데이터셋 생성 → 업로드 (검수·가공 후 자동 배포)
curl -X POST localhost:8080/api/datasets -H 'Content-Type: application/json' \
     -d '{"name":"gangnam-roads","type":"ROAD_NETWORK"}'
curl -X POST localhost:8080/api/datasets/gangnam-roads/versions \
     -F file=@roads.geojson -F autoPublish=true                  # → data.jobId

# 2) 작업 상태 / 검수 리포트 확인
curl localhost:8080/api/jobs/1
curl localhost:8080/api/datasets/gangnam-roads/versions/1/validation

# 3) 경로탐색 (보행, A*)
curl "localhost:8080/api/routes?dataset=gangnam-roads&mode=WALK&fromLon=127.0276&fromLat=37.4979&toLon=127.0364&toLat=37.5006"
```

---

## 💡 설계 포인트

**데이터·파이프라인**
- **좌표계 정규화**: 모든 geometry 는 EPSG:4326 으로 저장합니다. 래스터는 원본 좌표계를 유지하고 조회할 때 변환합니다. GDAL 3 의 축 순서 문제(EPSG:4326 = 위도, 경도)는 `OAMS_TRADITIONAL_GIS_ORDER`로 통일했습니다.
- **대량 적재는 JDBC batch, 메타데이터는 JPA**: 도로 링크·POI 는 `batchUpdate` + `reWriteBatchedInserts`로 적재합니다. 노드 키를 (버전 ID, 로컬 순번)으로 두어, 링크가 ID 조회 없이 노드를 바로 참조합니다.
- **긴 작업은 트랜잭션 밖에서**: GDAL 변환이나 S3 전송 중에는 DB 커넥션을 잡지 않습니다. 파이프라인은 작업 생성이 커밋된 뒤(`@TransactionalEventListener(AFTER_COMMIT)`) 전용 스레드 풀에서 시작합니다.
- **멱등 재시도**: 가공 단계는 해당 버전의 적재분을 지우고 한 트랜잭션으로 다시 적재합니다. 몇 번 재시도해도 결과가 같습니다.
- **검수 규칙 확장**: `ValidationRule<T>` 구현체를 `@Component`로 추가하면 자동으로 실행됩니다. 새 데이터 타입은 `DatasetProcessor` 구현체를 추가해 지원합니다.

**조회·경로탐색**
- **COG + `/vsis3/` + 핸들 풀**: 조회할 때 파일 전체를 내려받지 않고, 필요한 타일만 HTTP Range 로 읽습니다. 연 래스터는 버전별 풀(`RasterHandlePool`)에서 재사용합니다. GDAL Dataset 은 스레드 안전하지 않으므로 한 번에 한 요청에만 빌려줍니다.
- **버전 기반 캐시**: 서비스 버전(`ActiveVersionResolver`)과 경로 그래프 캐시는 배포 커밋 직후 즉시 무효화됩니다. 다중 인스턴스에 대비해 TTL 도 둡니다. DEM 재배포와 그래프 로드가 동시에 일어나는 경쟁 조건은 고도 세대 번호로 막습니다.
- **CSR 불변 그래프**: 배열 기반 그래프라 메모리 지역성이 좋고, 여러 요청이 잠금 없이 동시에 탐색할 수 있습니다. 이진 힙은 원시 배열로 직접 구현해 박싱과 GC 부담을 줄였습니다.
- **A\* 최적성**: 휴리스틱(직선거리 / 수단별 최대속도)이 실제 비용을 과대평가하지 않고(admissible) 일관적이므로(consistent), Dijkstra 와 같은 최적해를 보장합니다. 테스트에서 Bellman-Ford 와도 대조했습니다.
- **이동수단 비용**: 차량은 제한속도 또는 도로 등급별 기본속도를 씁니다. 보행은 Tobler 보행 함수, 자전거는 경사에 따른 감속·가속을 적용합니다. 보행·자전거는 일방통행과 무관하고 자동차전용도로는 지나지 않습니다.

**안정성**
- **GDAL 로딩 분리**: 네이티브 라이브러리가 없어도 앱은 기동하고, GDAL 이 필요한 API 만 503 을 응답합니다.
- **장애 격리**: S3 SDK 와 GDAL HTTP 에 타임아웃을 둡니다. 스토리지 장애는 503 으로 응답하고, DB·메모리 기반 조회(POI, 경로)는 계속 동작합니다.
- **복구**: 서버가 작업 도중 종료되면, 기동할 때 그 작업을 FAILED 로 정리합니다. 이후 재시도하면 실패한 단계부터 이어서 처리합니다.

---

## 🧪 테스트

### 테스트 구성

```
            ┌──────────────┐  k6 부하 테스트, 알고리즘 벤치마크, A/B 비교          scripts/perf/
            │   성능        │
          ┌─┴──────────────┴─┐  실제 배포 환경(App+PostGIS+S3)에 HTTP 요청 47개   scripts/e2e/
          │   시스템 E2E       │  기능 · 오류 · 동시성 · 장애 주입
        ┌─┴──────────────────┴─┐  Spring + Testcontainers + GDAL (28개)          *IntegrationTest, *GdalTest
        │   통합                 │
      ┌─┴──────────────────────┴─┐  알고리즘 · 검수 규칙 · 자료구조 (53개)            *Test
      │   단위                     │
      └──────────────────────────┘
```

테스트 데이터는 GDAL/OGR 로 **결정적으로 생성**합니다. 그래서 기대값을 수식으로 정확히 계산할 수 있습니다.
예: 픽셀 (37,12)의 값은 68.5, 픽셀 경계에서 보간한 고도는 100.25, 격자 도로망의 경로 거리는 맨해튼 거리와 같아야 합니다.

### 시스템 E2E 결과 (47 / 47)

| 그룹 | 시나리오 | 주요 검증 |
|---|---|---|
| 데이터셋 | 4 | 생성·중복(409)·형식 오류(400)·없음(404) |
| 래스터 | 7 | 정상 DEM 배포, 좌표계 없음 / 손상 / 중복 / 영역 밖 불합격, NoData 경고 |
| 벡터 | 7 | 도로망 토폴로지 적재, Shapefile(5186) 재투영 정확성(1e-6°), 연결성 경고, 타입·이름 불합격 |
| 배포 | 6 | 버전 전환, 롤백, 재배포 멱등성, 불합격 버전 배포 거부, bbox 검색 |
| 조회 | 10 | 좌표 변환(중부원점 ±1cm, 왕복 1e-8°), 픽셀·보간 고도, 고도 프로파일, POI 반경·KNN |
| 경로 | 8 | 맨해튼 거리·소요 시간, 일방통행, 자동차전용도로, 도로 폐쇄 버전 즉시 반영, **DEM 언덕 우회** |
| 동시성 | 2 | 동시 업로드 10건 버전 번호 중복 0, 배포 전환 중 경로 요청 약 1,900건 오류 0 |
| 장애 | 2 | 가공 중 서버 재시작 → 실패 단계부터 재시도, S3 장애 시 503 + 조회 부분 가용성 |

### 테스트로 찾아 수정한 결함

| 결함 | 발견 | 수정 |
|---|---|---|
| 요청 본문의 잘못된 enum 값에 500 응답 | E2E | 400 으로 처리 |
| S3 장애가 `409 Conflict`로 응답 | 장애 시나리오 설계 | `StorageException` → 503 |
| S3·GDAL HTTP 타임아웃 없음 (장애 시 무기한 대기) | 장애 시나리오 설계 | 타임아웃 설정 → 장애 응답 6.4초 |
| DEM 재배포 중 그래프 로드 경쟁 조건 | 경사 시나리오 설계 | 고도 세대 번호로 오래된 그래프 폐기 |
| 고도·픽셀 조회마다 COG 열기 (p95 136ms) | 성능 테스트 | 래스터 핸들 풀 → p95 8.5ms |
| 성능 측정에 오류 응답(503)이 섞여도 감지 못 함 | 측정 결과 검증 | 시나리오별 오류율 검사, 데이터 가용성 사전 확인 |

### 성능 개선 A/B (10 VU, 워밍업 후 3회 중앙값, 오류율 0%)

| API | 개선 전 p95 | 개선 후 p95 | 변화 | 판정 |
|---|---|---|---|---|
| 고도 조회 | 135.9ms | 8.5ms | **-94%** | ✅ 3회 범위가 겹치지 않음 |
| 픽셀 값 | 145.9ms | 7.8ms | **-95%** | ✅ |
| 고도 100점 배치 | 136.0ms | 19.6ms | **-86%** | ✅ |
| 경로탐색 (A\*) | 25.4ms | 18.9ms | -26% | ➖ 측정 잡음 범위 (효과 주장 안 함) |

개선 전·후 이미지를 같은 DB/S3 상태에서 번갈아 측정하고, 3회 측정 범위가 겹치지 않을 때만 개선으로 판정했습니다.
이 로컬 환경(Windows + WSL2, 부하 발생기 동일 PC)에서는 같은 조건에서도 p95 가 20~97% 흔들렸습니다.

### 알고리즘·자료구조 측정

| 항목 | 결과 |
|---|---|
| A\* vs Dijkstra (100×100 격자, 서버 측 400쌍) | 확정 노드 **47%**, 탐색 시간 p50 1.43 → 0.98ms, 거리가 짧을수록 효과 큼 (0~2km: 30%) |
| A\* 최적성 | Bellman-Ford 대조 + 무작위 격자 600 질의 + 실제 도로망 600쌍, 불일치 **0건** |
| 격자 공간 인덱스 vs 전수 탐색 (점 10만 개, 최근접 1,000 질의) | 17ms vs 9.7s, 약 **570배** |

---

## 🚀 실행 방법

### 전체 스택 (Docker, 권장)

GDAL 네이티브 라이브러리를 설치하지 않아도 실행됩니다.

```bash
docker compose --profile app up -d --build   # App(GDAL 포함) + PostGIS + LocalStack(S3)
curl localhost:8080/api/datasets
```

### 로컬 개발

```bash
docker compose up -d        # PostGIS + LocalStack (geogdal-files 버킷 자동 생성)
./gradlew bootRun           # GDAL 3.6 네이티브 라이브러리 필요 (java.library.path)
```
> [GDAL 3.6.3 Windows 빌드](https://www.gisinternals.com/query.html?content=filelist&file=release-1930-x64-gdal-3-6-3-mapserver-8-0-0.zip)

### 주요 설정 (환경 변수)

| 설정 | 기본값 | 설명 |
|---|---|---|
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | `localhost:5432/geogdal` | PostGIS 접속 정보 |
| `AWS_S3_ENDPOINT` | `http://localhost:4566` | S3 엔드포인트 (비우면 AWS) |
| `ELEVATION_DATASET` | (없음) | 고도 조회·경로 경사 반영에 쓸 DEM 데이터셋 |
| `ROUTING_DATASET` | (없음) | 기본 도로 네트워크 데이터셋 |
| `geo.routing.max-snap-distance-m` | 500 | 좌표 → 도로 스냅 허용 거리 (m) |
| `geo.raster.pool.max-idle-per-raster` | 16 | 래스터별 유휴 핸들 최대 수 |
| `geo.active-version-cache.ttl` | 5s | 서비스 버전 캐시 TTL (다중 인스턴스 대비) |

### 테스트 실행

```bash
# 단위 + PostGIS 통합 (GDAL 이 필요한 테스트는 자동 skip)
./gradlew test

# 전체 JUnit (GDAL + PostGIS + LocalStack)
docker build -t geogdal-gdal docker/gdal
docker run --rm -v "$PWD":/src:ro -v geogdal-gradle:/root/.gradle \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
  geogdal-gdal bash /src/docker/gdal/run-tests.sh

# 시스템 테스트: 스택 기동 + 테스트 데이터 생성 + E2E 47개 (+ 성능)
bash scripts/run-system-tests.sh
bash scripts/run-system-tests.sh --perf

# 성능 A/B 비교
python scripts/perf/bench.py --label before --runs 3
python scripts/perf/bench.py --label after  --runs 3 --compare before
```

---

## 🗂️ 프로젝트 구조

```
src/main/java/com/minju/geogdalservice
├── controller/     REST API (데이터셋, 작업, 래스터, 고도, 좌표, POI, 경로)
├── pipeline/       수집·검수·가공·배포 (IngestService, PipelineRunner, PublishService, DatasetProcessor)
├── validation/     검수 규칙 엔진 (ValidationRule, 래스터/벡터 규칙)
├── gis/            GDAL 래퍼 (RasterInspector, VectorReader, OpenRaster, RasterHandlePool), GridIndex
├── network/        도로 토폴로지 구성 (RoadNetworkBuilder, UnionFind)
├── routing/        경로탐색 (RoadGraph, ShortestPath, MinHeap, TravelMode, RoadGraphProvider)
├── service/        조회 서비스 (고도, 픽셀, 좌표, POI, ActiveVersionResolver)
├── entity/ repository/ dto/ config/ common/ util/
src/main/resources/db/migration   Flyway (V1 초기, V2 공간 모델, V3 파이프라인)
scripts/e2e/        테스트 데이터 생성, E2E 시나리오 러너
scripts/perf/       k6 부하 테스트, A/B 벤치마크, 알고리즘 비교
docker/             GDAL 테스트 이미지, LocalStack 초기화
docs/               테스트 계획, 테스트 결과 비교 분석
```

---

## 🚨 트러블 슈팅

| 문제 | 원인 | 해결 |
|---|---|---|
| GDAL 이 없는 환경에서 앱 전체가 기동되지 않음 | `static { gdal.AllRegister(); }` 에서 `UnsatisfiedLinkError` | `GdalInitializer` 로 로딩 실패를 기록하고, GDAL 이 필요한 API 만 503 응답 |
| GeoTIFF 업로드가 거부됨 | 클라이언트가 `application/octet-stream` 으로 전송 | Content-Type 대신 확장자 + GDAL Open 으로 검증 |
| 잘못된 EPSG 요청이 500 | GDAL OSR 오류가 반환 코드가 아니라 `RuntimeException("OGR Error")` | 예외를 잡아 400 으로 변환 |
| 경로 고도 프로파일의 오르막이 실제보다 작음 | 일정 간격 샘플링이 꺾이는 정점을 건너뜀 | 샘플에 폴리라인 정점을 항상 포함 |
| S3 장애가 `409 Conflict`, 응답 무기한 대기 | `IllegalStateException` 재사용, 타임아웃 없음 | `StorageException` → 503, S3·GDAL HTTP 타임아웃 |
| DEM 재배포 직후 이전 고도로 경로 계산 | 무효화 비동기 처리 + 진행 중이던 그래프 로드가 캐시에 재삽입 | 무효화는 커밋 직후 동기로, 고도 세대 번호로 오래된 그래프 폐기 |
| 고도·픽셀 조회 p95 136ms | 요청마다 S3 의 COG 를 열기 (HEAD + 헤더 Range + PROJ 초기화) | 버전별 열린 핸들 풀 → 8.5ms |
| 성능이 좋아졌다고 판단했는데 실제로는 503 | 장애 시나리오가 S3 를 초기화한 뒤 측정해 빠른 오류 응답이 섞임 | 시나리오별 오류율 검사, 오류 섞인 측정 무효 처리, 측정 전 데이터 가용성 확인 |
| POI KNN 이 느리다고 오진 (16.6ms) | `psql -c` 새 커넥션의 첫 geography 연산 초기화 비용을 쿼리 비용으로 오인 | 앱과 같은 조건(웜 세션)에서 재측정 → 0.2~0.7ms, 코드 변경 없음 |
| 테스트 컨텍스트가 여러 개일 때 S3 조회 실패 | GDAL `/vsis3/` 설정이 프로세스 전역 | 테스트 LocalStack 컨테이너를 JVM 단위로 공유 |

---

## 📌 향후 개선사항

- 벡터 타일(MVT, `ST_AsMVT`) 서빙 API
- 실제 데이터 적재: 국가표준노드링크, OSM (교차로 분할 전처리), 국토지리정보원 DEM
- 경로탐색 고도화: 양방향 탐색, Contraction Hierarchies, 회전 제한, pgRouting 교차 검증
- 잡음이 적은 환경(리눅스, 부하 발생기 분리)에서 성능 재측정, 다중 인스턴스 캐시 일관성 테스트
- Actuator + Prometheus 모니터링, CI 에 E2E 편입
- 대중교통·주소 데이터 타입 추가 (`DatasetProcessor` 구현체로 확장)

---

## 📄 문서

- [테스트 계획](docs/TEST_PLAN.md): 테스트 전략, 데이터, 47개 시나리오의 절차와 기대값, 결함 목록
- [테스트 결과 비교 분석](docs/TEST_RESULT_ANALYSIS.md): 기능·성능 개선 전/후 비교, 원인 분석, 측정 신뢰성, 알고리즘 분석
