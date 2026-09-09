# 🌍 geo-gdal-service

GDAL 기반의 GeoTIFF/COG/DEM 공간 데이터 처리 API입니다.  
위경도 좌표 기반 정보 추출, 좌표계 변환, 반경 검색, 고도 조회 등 다양한 GIS 처리를 REST API로 제공합니다.

---
## 🎯 프로젝트 기간

- MVP 개발 기간 : 2025.05.08 ~ 
- 프로젝트 인원 : 1인 (개인)
---

## 📌 주요 기능 API

1. GeoTIFF 메타데이터 조회 - 진행 완료
2. 위경도 기반 픽셀 값 추출 - 진행 완료
3. 좌표계 변환 - 진행 완료
4. 반경 내 위치 데이터 검색 - 진행 완료
5. GeoTIFF → COG 포맷 변환 - 진행 완료
6. 고도 정보 조회 - 진행 완료

모든 응답은 `CommonResponse` 형식(`message`, `status`, `data`)으로 반환됩니다.  
좌표 입력은 WGS84(EPSG:4326) 위경도 기준이며, 좌표계 변환 API의 좌표 순서는 `x = 경도/Easting`, `y = 위도/Northing` 입니다.

### 0) GeoTIFF 파일 관리

GDAL 처리 대상 파일은 `geo.storage.dir`(기본 `./data/rasters`)에 저장되며, 이후 API에서 `fileName`으로 참조합니다.

| Method | URL | 설명 |
|---|---|---|
| POST | `/api/rasters/files` | GeoTIFF 업로드 (`multipart/form-data`, 파라미터 `file`, `.tif`/`.tiff`만 허용) |
| GET | `/api/rasters/files` | 저장된 파일 목록 |
| GET | `/api/rasters/files/download?fileName=` | 파일 다운로드 |

```bash
curl -F "file=@dem.tif" http://localhost:8080/api/rasters/files
```

### 1) GeoTIFF 메타데이터 조회

| Method | URL | 설명 |
|---|---|---|
| POST | `/api/metadata/extract?fileName=` | GDAL로 메타데이터 추출 후 저장 (같은 파일명은 갱신) |
| POST | `/api/metadata` | 메타데이터 직접 저장 |
| GET | `/api/metadata?fileName=&bandCount=&width=` | 조건 검색 (null 조건은 무시) |

```json
{
  "fileName": "dem.tif", "width": 10, "height": 10, "bandCount": 1,
  "driver": "GTiff", "dataType": "Float32", "crs": "EPSG:32652",
  "pixelSizeX": 100.0, "pixelSizeY": 100.0,
  "minX": 300000.0, "minY": 4199000.0, "maxX": 301000.0, "maxY": 4200000.0
}
```

### 2) 위경도 기반 픽셀 값 추출

| Method | URL | 설명 |
|---|---|---|
| GET | `/api/rasters/pixel?fileName=&lat=&lon=&band=` | 위경도 → 래스터 좌표계 변환 → 픽셀 위치의 밴드 값 반환 (`band` 생략 시 전체 밴드) |

- 래스터 범위 밖 좌표는 `400`
- NoData 픽셀은 `value: null`, `noData: true`

```json
{
  "fileName": "dem.tif", "lat": 37.9, "lon": 126.7, "pixelX": 3, "pixelY": 4,
  "bands": [{ "band": 1, "dataType": "Float32", "value": 43.0, "noData": false }]
}
```

### 3) 좌표계 변환

| Method | URL | 설명 |
|---|---|---|
| GET | `/api/coordinates/transform?sourceCrs=&targetCrs=&x=&y=` | 단일 좌표 변환 |
| POST | `/api/coordinates/transform` | 다건 좌표 변환 (최대 10,000건) |

CRS는 `EPSG:xxxx`, WKT, PROJ 문자열을 모두 지원합니다.

```json
// POST /api/coordinates/transform
{
  "sourceCrs": "EPSG:4326",
  "targetCrs": "EPSG:5186",
  "points": [{ "x": 127.0, "y": 38.0 }, { "x": 126.9779, "y": 37.5663, "z": 10.0 }]
}
```

### 4) 반경 내 위치 데이터 검색

| Method | URL | 설명 |
|---|---|---|
| POST | `/api/locations` | 위치 등록 (`name`, `category`, `latitude`, `longitude`, `description`) |
| POST | `/api/locations/bulk` | 위치 일괄 등록 |
| GET | `/api/locations/{id}` | 위치 조회 |
| DELETE | `/api/locations/{id}` | 위치 삭제 |
| GET | `/api/locations/nearby?lat=&lon=&radius=&category=&limit=` | 반경(m) 내 위치를 가까운 순으로 조회 (`limit` 기본 100, 최대 1000) |

검색 방식: 경계 사각형(Bounding Box)으로 DB에서 1차 조회 → Haversine 거리로 2차 필터링 → 거리순 정렬.  
날짜변경선(±180°)이나 극점에 걸리는 경우 경도 범위를 전체로 확장해 누락을 방지합니다.

### 5) GeoTIFF → COG 포맷 변환

| Method | URL | 설명 |
|---|---|---|
| POST | `/api/rasters/cog` | GDAL COG 드라이버로 변환 후 저장소에 저장 |

| 필드 | 기본값 | 설명 |
|---|---|---|
| `fileName` | (필수) | 원본 파일명 |
| `outputFileName` | `{원본}_cog.tif` | 결과 파일명 |
| `compression` | `DEFLATE` | `NONE`, `LZW`, `DEFLATE`, `ZSTD`, `JPEG`, `WEBP`, `LERC` |
| `blockSize` | `512` | 타일 크기 (64 ~ 4096) |
| `overwrite` | `false` | 결과 파일이 있을 때 덮어쓰기 여부 (`false`이면 `409`) |

응답의 `layout` 값이 `COG`이면 GDAL이 유효한 COG로 인식한 파일입니다.

### 6) 고도 정보 조회

| Method | URL | 설명 |
|---|---|---|
| GET | `/api/elevation?fileName=&lat=&lon=&interpolation=&band=` | 단일 지점 고도 조회 |
| POST | `/api/elevation` | 다중 지점 고도 조회 (`fileName`, `interpolation`, `band`, `points[{lat, lon}]`) |

- `interpolation`: `NEAREST`(기본) / `BILINEAR`(주변 4픽셀 이중선형 보간, 주변에 NoData가 있으면 최근접 값 사용)
- 지점별 `status`: `OK`, `NO_DATA`, `OUT_OF_BOUNDS`
- 밴드의 scale/offset이 정의되어 있으면 적용한 값을 반환하고, 단위(`unit`)는 밴드 메타데이터를 따릅니다.

---

## 🔧 기술 스택

- Java 17
- Spring Boot 3.4.0
- Spring Web, Spring Data JPA, Spring Validation
- H2 Database (개발용)
- GDAL 3.6.3 Java Bindings (`libs/gdal.jar` + 네이티브 라이브러리, 시스템에 설치 필요)
> [GDAL 다운로드 링크](https://www.gisinternals.com/query.html?content=filelist&file=release-1930-x64-gdal-3-6-3-mapserver-8-0-0.zip)

---

## ⚙️ 실행 방법

### GDAL 네이티브 라이브러리 설정 (Windows)

위 링크의 zip을 받아 압축을 푼 뒤(예: `C:\gdal`), 환경 변수를 설정합니다.

```powershell
$env:PATH     = "C:\gdal\bin;C:\gdal\bin\gdal\java;$env:PATH"   # gdal.dll, gdalalljni.dll
$env:GDAL_DATA = "C:\gdal\bin\gdal-data"
$env:PROJ_LIB  = "C:\gdal\bin\proj9\share"                     # proj.db
```

- `libs/gdal.jar`는 GDAL 3.6.3용이므로 네이티브 라이브러리도 같은 버전을 사용해야 합니다.
- 네이티브 라이브러리를 찾지 못해도 애플리케이션은 기동되며, GDAL이 필요한 API는 `503`을 반환합니다. (반경 검색 API는 GDAL 없이 동작)

### 실행 / 테스트

```bash
./gradlew bootRun
./gradlew test
```

- GDAL 통합 테스트는 테스트용 DEM(EPSG:32652, 10x10)을 임시 디렉터리에 생성해 검증하며, GDAL을 로드할 수 없는 환경에서는 자동으로 건너뜁니다.
- H2 콘솔: `http://localhost:8080/h2-console` (JDBC URL `jdbc:h2:mem:geogdal`)

### 주요 설정 (`application.properties`)

| 키 | 기본값 | 설명 |
|---|---|---|
| `geo.storage.dir` | `./data/rasters` | GeoTIFF 저장 경로 |
| `spring.servlet.multipart.max-file-size` | `1GB` | 업로드 최대 크기 |

---

## 🗂️ 프로젝트 구조

```
src/main/java/com/minju/geogdalservice
├── common
│   ├── dto          # CommonResponse
│   ├── exception    # GeoServiceException (HTTP 상태 + 메시지)
│   └── handler      # GlobalExceptionHandler
├── config           # GdalInitializer (GDAL 드라이버 등록, 사용 가능 여부)
├── controller       # Metadata, RasterFile, PixelValue, CoordinateTransform, Location, CogConvert, Elevation
├── dto
├── entity           # Metadata, Location
├── repository
├── service          # 기능별 서비스, RasterStorageService(파일 저장소)
└── util             # GdalRasterSupport(Dataset/좌표→픽셀/밴드 읽기), GeoDistanceUtils(Haversine)
```

---
## 🚨 트러블 슈팅

- **GDAL 3.x 좌표 축 순서 문제**  
  GDAL 3부터 EPSG:4326은 권위(Authority) 정의에 따라 `위도, 경도` 순서로 처리되므로, 별도 설정 없이 `TransformPoint(lon, lat)`을 호출하면 위도/경도가 뒤바뀐 좌표로 변환됩니다.  
  → 모든 `SpatialReference`에 `SetAxisMappingStrategy(OAMS_TRADITIONAL_GIS_ORDER)`를 적용해 `x=경도, y=위도` 순서로 통일했습니다.
- **기존 코드 컴파일 오류**  
  `GlobalExceptionHandler`에 다른 프로젝트의 `com.siai.common.dto.CommonResponse` import가 남아 있었고, `MetadataDto`에 기본 생성자가 없어 `new MetadataDto()` 및 JSON 역직렬화가 불가능했습니다.  
  → 잘못된 import 제거, `MetadataDto`에 `@NoArgsConstructor`/`@AllArgsConstructor` 추가
- **네이티브 라이브러리 로딩 실패 (`UnsatisfiedLinkError`)**  
  `gdal.jar`만으로는 동작하지 않고 `gdalalljni.dll`과 의존 DLL이 `PATH`(또는 `java.library.path`)에 있어야 합니다. 로딩 실패 시 최초 호출은 `UnsatisfiedLinkError`, 이후 호출은 `NoClassDefFoundError`가 발생합니다.  
  → `GdalInitializer`에서 `LinkageError`를 잡아 애플리케이션 기동은 유지하고, GDAL API는 `503`으로 원인을 응답하도록 처리했습니다.
- **Dataset 미해제로 인한 파일 잠금 방지**  
  GDAL `Dataset`의 해제를 GC에 맡기면 파일 핸들이 늦게 닫혀, Windows에서 파일 교체/삭제가 실패할 수 있습니다.  
  → `GdalRasterSupport.withDataset()`에서 `try-finally`로 `delete()`를 보장하고, COG 변환은 임시 파일에 쓴 뒤 결과 파일로 교체합니다.
---

## 📌 향후 개선사항

- S3 연동: 현재 `S3ServiceImpl`은 스텁 상태이므로 AWS SDK 기반 업로드/다운로드 구현 후, S3의 COG를 `/vsis3/`로 직접 읽도록 개선
- 대용량 COG 변환 비동기 처리: 작업 ID 발급 → 상태 조회 방식으로 변경
- 반경 검색 공간 인덱스 적용: PostGIS/H2GIS의 공간 인덱스(`ST_DWithin`)로 대량 데이터 검색 성능 개선
- API 문서화: springdoc-openapi(Swagger UI) 적용
