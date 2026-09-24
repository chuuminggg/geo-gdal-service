# 테스트 계획 및 시나리오

## 1. 목적과 범위

단위·통합 테스트(JUnit, 74개)는 **클래스/컴포넌트 단위의 정확성**을 검증한다.
이 문서의 시스템 테스트는 **실제로 배포된 애플리케이션**(Docker: App + PostGIS + LocalStack S3)에 HTTP 로 요청해 다음을 검증한다.

| 관점 | 검증 내용 |
|---|---|
| 기능 | 수집 → 검수 → 가공 → 배포 → 조회/경로탐색 전체 흐름이 API 로 동작하는가 |
| 데이터 정확성 | 좌표계 변환, 픽셀/고도 값, 거리, 경로가 **수학적으로 계산 가능한 기대값**과 일치하는가 |
| 오류 처리 | 잘못된 입력·상태에 대해 올바른 HTTP 상태(400/404/409/503)를 돌려주는가 |
| 동시성 | 동시 업로드, 배포 전환 중 조회에서 데이터 불일치·오류가 없는가 |
| 장애 복구 | 서버 재시작, 스토리지 장애 후 복구·재시도가 되는가 |
| 성능 | API 별 응답시간(p95)과 처리량, 알고리즘 효율 |

## 2. 테스트 레벨

```
            ┌──────────────┐  k6 부하 테스트, 알고리즘 벤치마크        scripts/perf/
            │   성능 (P)    │
          ┌─┴──────────────┴─┐  HTTP 시나리오 47개 (실제 배포 환경)    scripts/e2e/
          │   시스템 E2E (S)   │
        ┌─┴──────────────────┴─┐  Spring + Testcontainers + GDAL        src/test/**/*IntegrationTest
        │   통합 테스트 (I)      │
      ┌─┴──────────────────────┴─┐  알고리즘/규칙/자료구조               src/test/**/*Test
      │       단위 테스트 (U)       │
      └──────────────────────────┘
```

## 3. 테스트 환경

| 구성 | 내용 |
|---|---|
| App | `Dockerfile` (GDAL 3.6.3 + JDK 17), `ELEVATION_DATASET=e2e-dem` |
| DB | PostGIS 16-3.4 |
| 스토리지 | LocalStack 3.8 (S3, `geogdal-files` 버킷) |
| 실행 | `docker compose --profile app up -d --build` |
| 도구 | Python 3 표준 라이브러리(시나리오), GDAL Python(데이터 생성), k6(부하) |

## 4. 테스트 데이터

`scripts/e2e/generate_data.py` 가 결정적인(deterministic) 데이터를 생성한다. 값이 수식으로 정해져 있어 기대값을 정확히 계산할 수 있다.
`--seed` 는 결과에 영향이 없는 모서리 픽셀 한 개만 바꿔 **재실행 시 체크섬 중복으로 거부되지 않게** 한다.

| 파일 | 내용 | 용도 |
|---|---|---|
| `dem_seoul_5186.tif` | EPSG:5186, 400×400, 30m, 고도 = 50 + 0.5×col, 좌상단 10×10 NoData | 정상 래스터, 픽셀/고도/프로파일 |
| `dem_nocrs.tif` / `corrupted.tif` / `image.png` | 좌표계 없음 / 손상 파일 / 다른 포맷 | 검수 불합격, 업로드 거부 |
| `dem_tokyo_4326.tif` | 도쿄 영역 | 서비스 영역 검사 |
| `dem_sparse_5186.tif` | NoData 70% | 경고(WARN) 후 통과 |
| `dem_hill_4326.tif` | 도로 중간 지점만 60m 언덕 | 경사 반영 경로 |
| `dem_big_5186.tif` | 6000×6000 | 가공 중 재시작 |
| `roads_grid.geojson` | 20×20 격자 (가로 30km/h, 세로 50km/h) | 경로 거리/시간 기대값 |
| `roads_grid_5186_shp.zip` | 위 격자를 EPSG:5186 Shapefile 로 변환 | zip/재투영 경로 |
| `roads_oneway/motorway/closure_v1/v2.geojson` | 직선 A-B + 북쪽 우회로 사각형 | 일방통행, 자동차전용, 도로 폐쇄 |
| `roads_hill.geojson` | 언덕 직선 + 평지 우회 | 경사 반영 |
| `roads_disconnected.geojson` / `roads_points.geojson` | 섬 3개 / 포인트 | 연결성 경고, 타입 불합격 |
| `pois*.geojson` | 강남 일대 지하철역·카페 / 이름 누락 / 도쿄 | POI 검색, 검수 |
| `roads_perf.geojson` / `pois_perf.geojson` | 100×100 격자(링크 19,800) / POI 20,000 | 성능 |

## 5. 시스템 테스트 시나리오 (`scripts/e2e/run_scenarios.py`)

### 5.1 데이터셋 관리
| ID | 시나리오 | 기대 결과 |
|---|---|---|
| TC-DS-01 | 데이터셋 생성 | 201, 서비스 버전 없음 |
| TC-DS-02 | 같은 이름으로 다시 생성 | 409 |
| TC-DS-03 | 공백/대문자 이름, 없는 타입(`LIDAR`) | 400 |
| TC-DS-04 | 없는 데이터셋 조회/업로드 | 404 |

### 5.2 래스터 수집·검수·가공
| ID | 시나리오 | 기대 결과 |
|---|---|---|
| TC-RA-01 | 정상 DEM(5186) 업로드, autoPublish | PUBLISHED, EPSG 5186, 해상도 30m, NoData 100/160,000, 최소값 50, COG 가 `processed/` 에 저장, 상태 이력 6단계, 실패 규칙 없음 |
| TC-RA-02 | 좌표계 없는 래스터 | 작업 SUCCEEDED, 버전 REJECTED, `RASTER_CRS` ERROR, 배포 안 됨 |
| TC-RA-03 | 손상된 파일 | REJECTED, `READABLE` 만 실패 |
| TC-RA-04 | 같은 파일을 다른 이름으로 재업로드 | v2 REJECTED(`DUPLICATE_FILE`), 서비스 버전 v1 유지 |
| TC-RA-05 | 도쿄 DEM | REJECTED(`WITHIN_SERVICE_AREA`) |
| TC-RA-06 | NoData 70% | `RASTER_NODATA_RATIO` WARN, PROCESSED |
| TC-RA-07 | RASTER 에 .png / .geojson | 400 |

### 5.3 벡터 수집·검수·가공
| ID | 시나리오 | 기대 결과 |
|---|---|---|
| TC-VE-01 | 20×20 격자 도로망 | PUBLISHED, 링크 760, 노드 400, 연결성 통과 |
| TC-VE-02 | 같은 도로망의 Shapefile(zip, 5186) | 피처 수 동일, footprint 가 GeoJSON 과 1e-6도 이내 (재투영 정확성) |
| TC-VE-03 | 섬 3개 도로망 | `ROAD_CONNECTIVITY` WARN("연결 요소 3개"), PROCESSED |
| TC-VE-04 | 도로 데이터셋에 포인트 | REJECTED(`GEOMETRY_TYPE`) |
| TC-VE-05 | POI 6건 | PUBLISHED, 6건 |
| TC-VE-06 | 이름 없는 POI 75% | REJECTED(`POI_NAME`) |
| TC-VE-07 | 도쿄 POI | REJECTED(`WITHIN_SERVICE_AREA`) |

### 5.4 배포·버전 관리
| ID | 시나리오 | 기대 결과 |
|---|---|---|
| TC-PB-01 | REJECTED 버전 배포 | 409 |
| TC-PB-02 | v1 배포 후 v2 배포 | v1 ARCHIVED, v2 PUBLISHED |
| TC-PB-03 | 롤백 | v1 재배포, v2 ARCHIVED, v1 이력 `PUBLISHED → ARCHIVED → PUBLISHED` |
| TC-PB-04 | 서비스 중인 버전 재배포 | 200, 이력 추가 없음 (멱등) |
| TC-PB-05 | 없는 버전 배포 / 이전 버전 없이 롤백 | 404 / 409 |
| TC-PB-06 | bbox 검색 | 배포된 DEM 만 포함, 미배포 제외, 다른 지역 bbox 에는 없음 |

### 5.5 공간 조회
| ID | 시나리오 | 기대 결과 |
|---|---|---|
| TC-QY-01 | (127, 38) 4326 → 5186 | (200000, 600000) ± 1cm (중부원점 정의) |
| TC-QY-02 | 서울·부산·제주 4326 ↔ 5179 왕복 | 1e-8도 이내 |
| TC-QY-03 | 없는 EPSG, 빈 좌표 | 400 |
| TC-QY-04 | 5186 픽셀(37,12) 중심을 경위도로 조회 | 픽셀 (37,12), 값 68.5 / NoData 픽셀 null / 범위 밖 inside=false |
| TC-QY-05 | 픽셀 중심, 두 픽셀 경계 고도 | 100.0 / 100.25 (쌍선형 보간), 다건 조회에서 범위 밖 null |
| TC-QY-06 | col 20 → 220 → 20 왕복 프로파일 | 오르막 100m, 내리막 100m, 최고 160m, 길이 12km |
| TC-QY-07 | 미배포 래스터 조회 | 404 |
| TC-QY-08 | 강남역 1.2km 지하철역 / 500m 카페 | [강남역, 역삼역] 거리순, 역삼역 거리 = 하버사인 ± 5m / [강남역 카페] |
| TC-QY-09 | 서울역 부근 KNN k=2 | [서울역, 강남역 카페], 거리 오름차순 |
| TC-QY-10 | 반경 50km, k=0, 경도 200 | 400 |

### 5.6 경로탐색
| ID | 시나리오 | 기대 결과 |
|---|---|---|
| TC-RT-01 | 격자 (0,0) → (19,19) 차량 | 거리 = 가로 + 세로 (± 1m), 시간 = 가로/30km/h + 세로/50km/h |
| TC-RT-02 | Dijkstra vs A* 비교 | 비용 동일, A* 확정 노드 < Dijkstra |
| TC-RT-03 | 일방통행 (B→A 방향) | 차량 A→B 우회, 차량 B→A 직진, 보행 A→B 직진 |
| TC-RT-04 | 자동차전용도로 직선 | 차량 직진, 보행·자전거 우회 |
| TC-RT-05 | 직선 폐쇄 버전(v2) 배포 → 롤백 | v1 직진 → v2 우회(versionNo=2) → 롤백 후 직진 |
| TC-RT-06 | 언덕 DEM 을 `e2e-dem` 으로 배포 | 보행은 평지 우회(누적 오르막 0), 차량은 직진(누적 오르막 60m) |
| TC-RT-07 | 끊어진 도로망 섬 사이 | 404 |
| TC-RT-08 | 도로에서 먼 좌표 / 잘못된 수단 / 미배포 도로망 / POI 데이터셋 | 400 / 400 / 404 / 400 |

### 5.7 동시성
| ID | 시나리오 | 기대 결과 |
|---|---|---|
| TC-CC-01 | 같은 데이터셋에 10건 동시 업로드 | 버전 번호 1~10 중복 없음 (행 잠금), 모두 PROCESSED |
| TC-CC-02 | 8개 스레드가 8초간 경로 요청하는 동안 v1/v2 배포 반복 | 오류 0건, 응답의 versionNo 와 경로(직진/우회)가 항상 일치, 두 버전 모두 관측 |

### 5.8 장애·복구 (`--with-docker`)
| ID | 시나리오 | 기대 결과 |
|---|---|---|
| TC-FT-01 | 6000×6000 래스터 COG 변환 중 `docker restart` | 재기동 후 작업 FAILED("재시작"), 버전 FAILED → 재시도 시 attempt 2, **검수 없이 가공 단계부터** 재개해 PROCESSED, 이전 작업 재시도는 409 |
| TC-FT-02 | LocalStack 중지 | 업로드 503(60초 이내), DB 기반 POI 조회·메모리 그래프 경로탐색은 200 유지, 재기동 후 업로드 정상 |

## 6. 성능 테스트 (`scripts/perf/`)

| 시나리오 | 부하 | 요청 | 임계값(p95) |
|---|---|---|---|
| route_astar / route_dijkstra / route_walk | 10 VU × 20~30s | 100×100 격자 임의 출발·도착 | 300 / 500 / 300ms |
| poi_nearby / poi_nearest | 10 VU | POI 20,000건, 반경 500m / k=10 | 100ms |
| elevation / pixel_value | 10 VU | DEM 범위 임의 좌표 | 150ms |
| elevation_batch | 10 VU | 100좌표 | 300ms |

`compare_algorithms.py` 는 HTTP 오버헤드를 뺀 **서버 측 탐색 시간과 확정 노드 수**로 두 알고리즘을 비교한다.

## 7. 실행 방법

```bash
bash scripts/run-system-tests.sh          # 스택 기동 + 데이터 생성 + E2E 47개
bash scripts/run-system-tests.sh --perf   # + 성능 테스트

# 개별 실행
python scripts/e2e/run_scenarios.py --only TC-RT TC-QY-04 --verbose
python scripts/perf/compare_algorithms.py --pairs 300 --mode WALK
k6 run -e SCENARIO=poi_nearest -e VUS=20 scripts/perf/load-test.js
```
결과: 콘솔 + `build/e2e-report.json`, `build/perf-summary.json`

## 8. 1회차 실행 결과 (2026-09-24)

### 8.1 E2E: 47 / 47 PASS (2회 연속, 약 80초)
- TC-RT-02: 확정 노드 Dijkstra 211 → A* 104
- TC-CC-02: 배포 전환 중 경로 요청 1,705~1,916건, 오류 0건
- TC-FT-02: 스토리지 장애 응답 6.3초

### 8.2 성능 - 1차 측정 (단발, 10 VU × 20초) ⚠️ 신뢰할 수 없음

첫 측정에서 poi_nearest p95 447ms, elevation 155ms, pixel_value 167ms 로 임계값을 넘었다.
이후 분석에서 이 측정 방식에 다음 문제가 있음을 확인했다 (→ 8.4, 9).
- 워밍업 없는 단발 측정: 같은 코드로 반복해도 p95 가 ±30~40% 흔들림 (예: poi_nearest 9.8~46.1ms)
- **오류 응답 혼입**: E2E 의 TC-FT-02 가 LocalStack 을 재시작하면서 S3 객체가 사라진 뒤 측정한 회차는
  고도/픽셀 요청의 15.6% 가 빠른 503 이었고, 그래서 응답시간이 좋아 보였다
- poi_nearest 447ms 는 이후 7회 측정에서 한 번도 재현되지 않음 (p95 9.8~46ms)

### 8.3 알고리즘 비교 (서버 측, 100×100 격자, 최적 비용 불일치 0건)

| 직선거리 | 확정 노드 (Dijkstra → A*) | 탐색 시간 p50 (Dijkstra → A*) |
|---|---|---|
| 0~2km | 441 → 131 (30%) | 0.12 → 0.07ms |
| 2~5km | 3,321 → 1,075 (32%) | 0.87 → 0.57ms |
| 5~20km | 7,028 → 3,629 (52%) | 1.92 → 1.67ms |
| 전체 CAR / WALK | 47% / 43% | 1.43 → 0.98 / 2.22 → 1.20ms |

### 8.4 성능 개선 A/B (워밍업 + 3회 반복 중앙값, 오류율 0% 확인)

`scripts/perf/bench.py` 로 개선 전 커밋(aa258e7)과 개선 후 이미지를 **같은 DB/S3 상태**에서 번갈아 측정했다. (단위 ms)

| API | before p50 / p95 | after p50 / p95 | p95 변화 | 3회 범위 겹침 | 판정 |
|---|---|---|---|---|---|
| elevation | 21.2 / 135.9 | 3.9 / 8.5 | **-94%** | 없음 (117~170 → 7.8~13.6) | ✅ 개선 확인 |
| pixel_value | 24.1 / 145.9 | 3.6 / 7.8 | **-95%** | 없음 (121~155 → 6.6~13.9) | ✅ 개선 확인 |
| elevation_batch (100점) | 24.2 / 136.0 | 7.1 / 19.6 | **-86%** | 없음 | ✅ 개선 확인 |
| route_astar | 9.4 / 25.4 | 6.5 / 18.9 | -26% | 있음 | ➖ 잡음 범위 |
| route_dijkstra | 8.7 / 21.5 | 8.7 / 28.3 | +32% | 있음 | ➖ 잡음 범위 |
| route_walk | 7.2 / 18.7 | 6.1 / 16.2 | -14% | 있음 | ➖ 잡음 범위 |
| poi_nearby / nearest | 9.1 / 24.1, 5.7 / 11.8 | 12.9 / 32.8, 7.5 / 19.7 | (코드 변경 없음) | 있음 | ➖ 환경 잡음 |

## 9. 발견된 결함과 조치

| # | 발견 경로 | 내용 | 조치 |
|---|---|---|---|
| 1 | TC-DS-03 | JSON 본문의 잘못된 enum 값(`"type":"LIDAR"`)이 500 | `HttpMessageNotReadableException` → 400 **(수정)** |
| 2 | TC-FT-02 설계 | S3 장애가 `IllegalStateException` → **409 Conflict** 로 응답 | `StorageException` → 503 **(수정)** |
| 3 | TC-FT-02 설계 | S3 SDK / GDAL HTTP 에 타임아웃이 없어 스토리지 장애 시 요청이 무기한 대기할 수 있음 | S3 api-call(-attempt) 타임아웃, `GDAL_HTTP_(CONNECT)TIMEOUT` **(수정)** |
| 4 | TC-RT-06 설계 | DEM 재배포와 동시에 진행 중이던 그래프 로드가 이전 고도로 만든 그래프를 캐시에 다시 넣을 수 있는 경쟁 조건 | 고도 세대(generation) 번호를 그래프와 함께 저장, 불일치 시 재로드 **(수정)** |
| 5 | 성능 | POI KNN p95 447ms | **결함 아님으로 정정.** 새 커넥션의 첫 geography 연산 비용(PostGIS 백엔드 초기화)을 쿼리 비용(16.6ms)으로 오인했음. 웜 세션에서 기존 geography KNN 은 0.2~0.7ms, generic plan 에서도 인덱스 사용. 대안(geometry KNN 상한 + geography 반경)은 0.7~1.6ms 로 오히려 느려서 적용하지 않음 |
| 6 | 성능 | 고도/픽셀 조회 p95 136~146ms. 요청마다 `/vsis3/` COG 열기(HEAD·헤더 Range 요청, 좌표변환 객체 생성) | **수정**: 버전별 경로 단위 열린 핸들 풀(`RasterHandlePool`, 스레드당 1개 대여, 배포 시 이전 경로 폐기) → p95 **-94~95%** |
| 7 | 성능 | 경로 요청마다 DB 에서 active 버전 조회 | **수정**: `ActiveVersionResolver`(배포 커밋 시 즉시 무효화 + TTL 5초). 지연시간 변화는 이 환경의 잡음(±30%) 안이라 효과를 주장하지 않음. 조회 경로에서 DB 의존을 없앤 것(DB 부하 감소, 짧은 DB 장애 시에도 조회 유지)이 주된 이점 |
| 8 | 측정 | 성능 측정에 오류 응답이 섞여도 감지하지 못함, 장애 시나리오 후 데이터가 사라진 채 측정 | **수정**: 시나리오별 오류율 기록 + 오류 섞인 측정은 무효 처리(`bench.py`), `prepare.py` 가 실제 조회로 데이터 가용성을 확인하고 필요하면 재적재 |

### 측정에서 얻은 교훈
- 오류 응답은 빠르다: 응답시간과 함께 **반드시 오류율을 같이 보고**, 오류가 섞인 측정은 버린다.
- 단발 측정은 믿지 않는다: 워밍업 후 반복 측정, 중앙값과 **범위가 겹치는지**로 개선 여부를 판단한다.
- `psql -c` 처럼 매번 새 커넥션으로 측정한 쿼리 시간에는 백엔드 초기화 비용이 섞인다. 커넥션 풀을 쓰는 앱과 같은 조건(같은 세션)에서 측정한다.

## 10. 다음 단계
- 잡음이 적은 환경(리눅스 호스트, 부하 발생기 분리)에서 경로/POI 재측정
- 실제 데이터(국가표준노드링크, SRTM/국토지리정보원 DEM, OSM) 적재 테스트 - OSM 은 교차로에서 링크가 분리되지 않은(non-noded) 데이터라 토폴로지 분할 전처리 필요
- CI 에 E2E(장애 시나리오 제외) 편입
