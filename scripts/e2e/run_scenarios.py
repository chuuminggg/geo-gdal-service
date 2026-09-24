"""
geo-gdal-service 시스템(E2E) 테스트 시나리오

실행 중인 애플리케이션(docker compose --profile app up)의 HTTP API 를 호출해
수집 -> 검수 -> 가공 -> 배포 -> 조회/경로탐색 흐름과 오류/동시성/장애 상황을 검증한다.

    python scripts/e2e/run_scenarios.py --base-url http://localhost:8080 --data-dir build/e2e-data
    python scripts/e2e/run_scenarios.py --only TC-RT --verbose
    python scripts/e2e/run_scenarios.py --with-docker      # 재시작/스토리지 장애 시나리오 포함 (docker CLI 필요)

데이터는 generate_data.py 로 미리 생성해야 한다. 결과는 콘솔과 JSON 리포트로 출력된다.
"""
import argparse
import concurrent.futures
import json
import os
import subprocess
import sys
import time
from dataclasses import asdict

sys.path.insert(0, os.path.dirname(__file__))
from api import (Api, check, check_close, check_eq, check_status, haversine, run_all, scenario)  # noqa: E402

# ---- generate_data.py 와 동일한 기준값 ----
DEM = dict(origin_x=200_000, origin_y=550_000, pixel=30, width=400, height=400)
GRID = dict(lon0=127.020, lat0=37.490, step=0.001, n=20)
SQ = dict(lon0=127.100, lat0=37.500, dx=0.004, dy=0.002)
HILL = dict(lon0=127.200, lat0=37.500)
ELEVATION_DATASET = os.environ.get("ELEVATION_DATASET", "e2e-dem")


class Context:
    def __init__(self, api, data_dir, run_id, app_container, localstack_container):
        self.api = api
        self.data_dir = data_dir
        self.run_id = run_id
        self.app_container = app_container
        self.localstack_container = localstack_container
        self.state = {}

    def path(self, name):
        return os.path.join(self.data_dir, name)

    def name(self, prefix):
        return f"{prefix}-{self.run_id}"

    def new_dataset(self, prefix, dtype):
        name = self.name(prefix)
        r = self.api.create_dataset(name, dtype, f"E2E {self.run_id}")
        check_status(r, 200, f"데이터셋 생성({name})")
        return name


def dem_pixel_center(col, row):
    """EPSG:5186 DEM 픽셀 중심 좌표"""
    return [DEM["origin_x"] + (col + 0.5) * DEM["pixel"], DEM["origin_y"] - (row + 0.5) * DEM["pixel"]]


def sq(point):
    """사각형 도로망 꼭짓점 A(좌하) B(우하) C(좌상) D(우상)"""
    x, y, dx, dy = SQ["lon0"], SQ["lat0"], SQ["dx"], SQ["dy"]
    return {"A": [x, y], "B": [x + dx, y], "C": [x, y + dy], "D": [x + dx, y + dy]}[point]


DIRECT_M = haversine(SQ["lon0"], SQ["lat0"], SQ["lon0"] + SQ["dx"], SQ["lat0"])


# ===========================================================================
# 0. 환경
# ===========================================================================

@scenario("TC-ENV-01", "환경", "애플리케이션 API 응답 확인")
def env_up(ctx):
    check_status(ctx.api.get("/api/datasets"), 200)


# ===========================================================================
# 1. 데이터셋 관리
# ===========================================================================

@scenario("TC-DS-01", "데이터셋", "데이터셋 생성", depends=["TC-ENV-01"])
def ds_create(ctx):
    name = ctx.name("ds")
    r = ctx.api.create_dataset(name, "RASTER")
    check_status(r, 200)
    check_eq(r.body["status"], 201, "응답 status")
    check_eq(r.data["name"], name, "name")
    check_eq(r.data["activeVersionNo"], None, "activeVersionNo")
    ctx.state["ds"] = name


@scenario("TC-DS-02", "데이터셋", "중복 이름 생성 거부 (409)", depends=["TC-DS-01"])
def ds_duplicate(ctx):
    check_status(ctx.api.create_dataset(ctx.state["ds"], "RASTER"), 409)


@scenario("TC-DS-03", "데이터셋", "이름 형식/타입 검증 (400)")
def ds_invalid(ctx):
    check_status(ctx.api.create_dataset("Seoul DEM", "RASTER"), 400, "공백/대문자 이름")
    check_status(ctx.api.post("/api/datasets", {"name": ctx.name("bad"), "type": "LIDAR"}), 400, "없는 타입")


@scenario("TC-DS-04", "데이터셋", "존재하지 않는 데이터셋 (404)")
def ds_not_found(ctx):
    check_status(ctx.api.get(f"/api/datasets/{ctx.name('none')}"), 404)
    check_status(ctx.api.upload(ctx.name("none"), ctx.path("pois.geojson")), 404, "없는 데이터셋 업로드")


# ===========================================================================
# 2. 래스터 수집/검수/가공
# ===========================================================================

@scenario("TC-RA-01", "래스터", "정상 DEM(EPSG:5186) 업로드 -> 검수 통과 -> COG -> 자동 배포", depends=["TC-ENV-01"])
def ra_ok(ctx):
    name = ctx.new_dataset("dem", "RASTER")
    job = ctx.api.upload_and_wait(name, ctx.path("dem_seoul_5186.tif"), auto_publish=True)
    check_eq(job["status"], "SUCCEEDED", "작업 상태")
    check_eq(job["versionStatus"], "PUBLISHED", "버전 상태")

    v = ctx.api.version(name, 1)
    check(v["active"], "서비스 버전이어야 함")
    check(v["processedKey"].startswith(f"processed/{name}/v1/"), f"COG 키: {v['processedKey']}")
    raster = v["raster"]
    check_eq(raster["epsg"], 5186, "EPSG")
    check_eq((raster["width"], raster["height"], raster["bandCount"]), (400, 400, 1), "크기/밴드")
    check_close(raster["resolutionM"], 30.0, 1e-6, "해상도(m)")
    check_close(raster["nodataRatio"], 100 / 160_000, 1e-9, "NoData 비율")
    check_close(raster["minValue"], 50.0, 1e-6, "최소값")
    lons = [c[0] for c in v["footprint"]["coordinates"][0]]
    check(126.99 < min(lons) < 127.01, f"footprint 서쪽 경계 {min(lons)}")

    failed = [r for r in ctx.api.validation(name, 1) if not r["passed"]]
    check_eq(failed, [], "실패한 검수 규칙")
    history = [h["toStatus"] for h in ctx.api.get(f"/api/datasets/{name}/versions/1/history").data]
    check_eq(history, ["UPLOADED", "VALIDATING", "VALIDATED", "PROCESSING", "PROCESSED", "PUBLISHED"], "상태 이력")
    ctx.state["dem"] = name


@scenario("TC-RA-02", "래스터", "좌표계 없는 래스터 -> REJECTED(RASTER_CRS)")
def ra_nocrs(ctx):
    name = ctx.new_dataset("nocrs", "RASTER")
    job = ctx.api.upload_and_wait(name, ctx.path("dem_nocrs.tif"), auto_publish=True)
    check_eq(job["status"], "SUCCEEDED", "파이프라인은 정상 종료")
    check_eq(job["versionStatus"], "REJECTED", "버전 상태")
    check("RASTER_CRS" in ctx.api.failed_rules(name, 1, "ERROR"), "RASTER_CRS 불합격")
    check_eq(ctx.api.get(f"/api/datasets/{name}").data["activeVersionNo"], None, "배포되지 않음")
    ctx.state["rejected"] = name


@scenario("TC-RA-03", "래스터", "손상된 파일 -> REJECTED(READABLE)")
def ra_corrupted(ctx):
    name = ctx.new_dataset("broken", "RASTER")
    job = ctx.api.upload_and_wait(name, ctx.path("corrupted.tif"))
    check_eq(job["versionStatus"], "REJECTED", "버전 상태")
    check_eq(ctx.api.failed_rules(name, 1), ["READABLE"], "실패 규칙")


@scenario("TC-RA-04", "래스터", "동일 파일 재업로드 -> REJECTED(DUPLICATE_FILE)", depends=["TC-RA-01"])
def ra_duplicate(ctx):
    name = ctx.state["dem"]
    job = ctx.api.upload_and_wait(name, ctx.path("dem_seoul_5186.tif"), filename="copy.tif")
    check_eq(job["versionNo"], 2, "버전 번호")
    check_eq(job["versionStatus"], "REJECTED", "버전 상태")
    check_eq(ctx.api.failed_rules(name, 2), ["DUPLICATE_FILE"], "실패 규칙")
    check_eq(ctx.api.get(f"/api/datasets/{name}").data["activeVersionNo"], 1, "기존 서비스 버전 유지")


@scenario("TC-RA-05", "래스터", "서비스 영역 밖(도쿄) -> REJECTED(WITHIN_SERVICE_AREA)")
def ra_outside(ctx):
    name = ctx.new_dataset("tokyo", "RASTER")
    job = ctx.api.upload_and_wait(name, ctx.path("dem_tokyo_4326.tif"))
    check_eq(job["versionStatus"], "REJECTED", "버전 상태")
    check_eq(ctx.api.failed_rules(name, 1, "ERROR"), ["WITHIN_SERVICE_AREA"], "실패 규칙")


@scenario("TC-RA-06", "래스터", "NoData 70% -> 경고(WARN)만 기록하고 가공 완료")
def ra_sparse(ctx):
    name = ctx.new_dataset("sparse", "RASTER")
    job = ctx.api.upload_and_wait(name, ctx.path("dem_sparse_5186.tif"))
    check_eq(job["versionStatus"], "PROCESSED", "버전 상태")
    check_eq(ctx.api.failed_rules(name, 1, "WARN"), ["RASTER_NODATA_RATIO"], "경고 규칙")
    ctx.state["unpublished_raster"] = name


@scenario("TC-RA-07", "래스터", "허용되지 않은 확장자 업로드 거부 (400)", depends=["TC-RA-01"])
def ra_extension(ctx):
    check_status(ctx.api.upload(ctx.state["dem"], ctx.path("image.png")), 400, "RASTER 에 .png")
    check_status(ctx.api.upload(ctx.state["dem"], ctx.path("pois.geojson")), 400, "RASTER 에 .geojson")


# ===========================================================================
# 3. 벡터 수집/검수/가공
# ===========================================================================

@scenario("TC-VE-01", "벡터", "도로망 GeoJSON -> 토폴로지 구성 후 PostGIS 적재")
def ve_roads(ctx):
    name = ctx.new_dataset("grid", "ROAD_NETWORK")
    job = ctx.api.upload_and_wait(name, ctx.path("roads_grid.geojson"), auto_publish=True)
    check_eq(job["versionStatus"], "PUBLISHED", "버전 상태")
    n = GRID["n"]
    check_eq(ctx.api.version(name, 1)["featureCount"], 2 * n * (n - 1), "링크 수")
    connectivity = next(r for r in ctx.api.validation(name, 1) if r["ruleCode"] == "ROAD_CONNECTIVITY")
    check(connectivity["passed"], f"연결성: {connectivity['message']}")
    check(f"노드 {n * n}개" in connectivity["message"], connectivity["message"])
    ctx.state["grid"] = name


@scenario("TC-VE-02", "벡터", "Shapefile(zip, EPSG:5186) -> WGS84 변환 후 적재", depends=["TC-VE-01"])
def ve_shapefile(ctx):
    name = ctx.new_dataset("shp", "ROAD_NETWORK")
    job = ctx.api.upload_and_wait(name, ctx.path("roads_grid_5186_shp.zip"))
    check_eq(job["versionStatus"], "PROCESSED", "버전 상태")
    shp = ctx.api.version(name, 1)
    geojson = ctx.api.version(ctx.state["grid"], 1)
    check_eq(shp["featureCount"], geojson["featureCount"], "피처 수")
    # 5186 -> 4326 재투영 결과가 원본 GeoJSON 범위와 일치해야 함
    for a, b in zip(shp["footprint"]["coordinates"][0], geojson["footprint"]["coordinates"][0]):
        check_close(a[0], b[0], 1e-6, "footprint 경도")
        check_close(a[1], b[1], 1e-6, "footprint 위도")
    ctx.state["unpublished_roads"] = name


@scenario("TC-VE-03", "벡터", "끊어진 도로망 -> 경고(ROAD_CONNECTIVITY) 후 가공 완료")
def ve_disconnected(ctx):
    name = ctx.new_dataset("islands", "ROAD_NETWORK")
    job = ctx.api.upload_and_wait(name, ctx.path("roads_disconnected.geojson"))
    check_eq(job["versionStatus"], "PROCESSED", "버전 상태")
    warn = next(r for r in ctx.api.validation(name, 1) if r["ruleCode"] == "ROAD_CONNECTIVITY")
    check_eq(warn["severity"], "WARN", "심각도")
    check("연결 요소 3개" in warn["message"], warn["message"])
    ctx.state["islands"] = name


@scenario("TC-VE-04", "벡터", "도로 데이터셋에 포인트 -> REJECTED(GEOMETRY_TYPE)")
def ve_wrong_type(ctx):
    name = ctx.new_dataset("pts", "ROAD_NETWORK")
    job = ctx.api.upload_and_wait(name, ctx.path("roads_points.geojson"))
    check_eq(job["versionStatus"], "REJECTED", "버전 상태")
    check("GEOMETRY_TYPE" in ctx.api.failed_rules(name, 1, "ERROR"), "GEOMETRY_TYPE 불합격")


@scenario("TC-VE-05", "벡터", "POI 적재 및 배포")
def ve_poi(ctx):
    name = ctx.new_dataset("poi", "POI")
    job = ctx.api.upload_and_wait(name, ctx.path("pois.geojson"), auto_publish=True)
    check_eq(job["versionStatus"], "PUBLISHED", "버전 상태")
    check_eq(ctx.api.version(name, 1)["featureCount"], 6, "POI 수")
    ctx.state["poi"] = name


@scenario("TC-VE-06", "벡터", "이름 없는 POI 75% -> REJECTED(POI_NAME)")
def ve_poi_noname(ctx):
    name = ctx.new_dataset("noname", "POI")
    job = ctx.api.upload_and_wait(name, ctx.path("pois_noname.geojson"))
    check_eq(job["versionStatus"], "REJECTED", "버전 상태")
    check_eq(ctx.api.failed_rules(name, 1, "ERROR"), ["POI_NAME"], "실패 규칙")


@scenario("TC-VE-07", "벡터", "서비스 영역 밖 POI -> REJECTED(WITHIN_SERVICE_AREA)")
def ve_poi_outside(ctx):
    name = ctx.new_dataset("tokyo-poi", "POI")
    job = ctx.api.upload_and_wait(name, ctx.path("pois_tokyo.geojson"))
    check_eq(ctx.api.failed_rules(name, 1, "ERROR"), ["WITHIN_SERVICE_AREA"], "실패 규칙")
    check_eq(job["versionStatus"], "REJECTED", "버전 상태")


# ===========================================================================
# 4. 배포 / 버전 관리
# ===========================================================================

@scenario("TC-PB-01", "배포", "불합격 버전 배포 거부 (409)", depends=["TC-RA-02"])
def pb_rejected(ctx):
    check_status(ctx.api.publish(ctx.state["rejected"], 1), 409)


@scenario("TC-PB-02", "배포", "v1 배포 -> v2 배포 시 v1 ARCHIVED")
def pb_switch(ctx):
    name = ctx.new_dataset("closure-pb", "ROAD_NETWORK")
    for f in ("roads_closure_v1.geojson", "roads_closure_v2.geojson"):
        check_eq(ctx.api.upload_and_wait(name, ctx.path(f))["versionStatus"], "PROCESSED", f)
    check_status(ctx.api.publish(name, 1), 200, "v1 배포")
    check_status(ctx.api.publish(name, 2), 200, "v2 배포")
    check_eq(ctx.api.version(name, 1)["status"], "ARCHIVED", "v1 상태")
    check_eq(ctx.api.version(name, 2)["status"], "PUBLISHED", "v2 상태")
    check_eq(ctx.api.get(f"/api/datasets/{name}").data["activeVersionNo"], 2, "서비스 버전")
    ctx.state["closure_pb"] = name


@scenario("TC-PB-03", "배포", "롤백 -> 직전 버전 재배포", depends=["TC-PB-02"])
def pb_rollback(ctx):
    name = ctx.state["closure_pb"]
    r = ctx.api.post(f"/api/datasets/{name}/rollback")
    check_status(r, 200)
    check_eq(r.data["versionNo"], 1, "롤백 버전")
    check_eq(ctx.api.version(name, 2)["status"], "ARCHIVED", "v2 상태")
    history = [h["toStatus"] for h in ctx.api.get(f"/api/datasets/{name}/versions/1/history").data]
    check_eq(history[-3:], ["PUBLISHED", "ARCHIVED", "PUBLISHED"], "v1 이력")


@scenario("TC-PB-04", "배포", "서비스 중인 버전 재배포는 멱등 (200)", depends=["TC-PB-03"])
def pb_idempotent(ctx):
    name = ctx.state["closure_pb"]
    before = len(ctx.api.get(f"/api/datasets/{name}/versions/1/history").data)
    check_status(ctx.api.publish(name, 1), 200)
    after = len(ctx.api.get(f"/api/datasets/{name}/versions/1/history").data)
    check_eq(after, before, "이력 추가 없음")


@scenario("TC-PB-05", "배포", "없는 버전 배포 (404) / 롤백할 버전 없음 (409)", depends=["TC-VE-05"])
def pb_errors(ctx):
    check_status(ctx.api.publish(ctx.state["poi"], 99), 404, "없는 버전")
    check_status(ctx.api.post(f"/api/datasets/{ctx.state['poi']}/rollback"), 409, "이전 버전 없음")


@scenario("TC-PB-06", "배포", "bbox 공간 검색은 서비스 중인 버전만 반환", depends=["TC-RA-01", "TC-RA-06"])
def pb_bbox(ctx):
    r = ctx.api.get("/api/datasets/search", minLon=127.0, minLat=37.5, maxLon=127.05, maxLat=37.55, type="RASTER")
    names = [v["datasetName"] for v in r.data]
    check(ctx.state["dem"] in names, f"배포된 DEM 포함: {names}")
    check(ctx.state["unpublished_raster"] not in names, "미배포 데이터 제외")
    busan = ctx.api.get("/api/datasets/search", minLon=128.9, minLat=35.0, maxLon=129.2, maxLat=35.3).data
    check(ctx.state["dem"] not in [v["datasetName"] for v in busan], "부산 영역에는 없음")


# ===========================================================================
# 5. 공간 조회
# ===========================================================================

@scenario("TC-QY-01", "조회", "좌표계 변환: EPSG:4326 (127,38) -> EPSG:5186 원점")
def qy_transform(ctx):
    x, y = ctx.api.transform([[127.0, 38.0]], 4326, 5186)[0]
    check_close(x, 200_000, 0.01, "x")
    check_close(y, 600_000, 0.01, "y")
    r = ctx.api.get("/api/coordinates/transform", x=127.0, y=38.0, **{"from": 4326, "to": 5186})
    check_status(r, 200, "GET 변환")


@scenario("TC-QY-02", "조회", "좌표계 왕복 변환 (4326 <-> 5179 UTM-K)")
def qy_roundtrip(ctx):
    src = [[126.9780, 37.5665], [129.0756, 35.1796], [126.5312, 33.4996]]   # 서울, 부산, 제주
    utmk = ctx.api.transform(src, 4326, 5179)
    back = ctx.api.transform(utmk, 5179, 4326)
    for s, b in zip(src, back):
        check_close(b[0], s[0], 1e-8, "경도")
        check_close(b[1], s[1], 1e-8, "위도")


@scenario("TC-QY-03", "조회", "잘못된 EPSG / 좌표 형식 (400)")
def qy_transform_invalid(ctx):
    check_status(ctx.api.post("/api/coordinates/transform", {"from": 4326, "to": 999999, "coordinates": [[1, 1]]}), 400)
    check_status(ctx.api.post("/api/coordinates/transform", {"from": 4326, "to": 5186, "coordinates": []}), 400)


@scenario("TC-QY-04", "조회", "픽셀 값 추출 (원본 좌표계 5186, 경위도로 조회)", depends=["TC-RA-01", "TC-QY-01"])
def qy_pixel(ctx):
    lon, lat = ctx.api.transform([dem_pixel_center(37, 12)], 5186, 4326)[0]
    r = ctx.api.get(f"/api/rasters/{ctx.state['dem']}/value", lon=lon, lat=lat)
    check_status(r, 200)
    check_eq((r.data["pixelX"], r.data["pixelY"]), (37, 12), "픽셀 좌표")
    check_eq(r.data["values"], [50 + 0.5 * 37], "값")

    lon, lat = ctx.api.transform([dem_pixel_center(5, 5)], 5186, 4326)[0]
    check_eq(ctx.api.get(f"/api/rasters/{ctx.state['dem']}/value", lon=lon, lat=lat).data["values"], [None], "NoData")
    outside = ctx.api.get(f"/api/rasters/{ctx.state['dem']}/value", lon=129.0, lat=35.1).data
    check_eq(outside["inside"], False, "범위 밖")


@scenario("TC-QY-05", "조회", "DEM 고도: 쌍선형 보간 + 다건 조회", depends=["TC-RA-01"])
def qy_elevation(ctx):
    dem = ctx.state["dem"]
    # 픽셀 100 중심 -> 100.0, 픽셀 100/101 경계 -> 두 중심의 평균 100.25
    center, boundary = ctx.api.transform(
        [dem_pixel_center(100, 200), [DEM["origin_x"] + 101 * DEM["pixel"], DEM["origin_y"] - 200.5 * DEM["pixel"]]],
        5186, 4326)
    check_close(ctx.api.get("/api/elevation", lon=center[0], lat=center[1], dataset=dem).data["elevation"],
                100.0, 1e-3, "픽셀 중심 고도")
    check_close(ctx.api.get("/api/elevation", lon=boundary[0], lat=boundary[1], dataset=dem).data["elevation"],
                100.25, 1e-3, "경계 보간 고도")
    batch = ctx.api.post("/api/elevation/batch", {"dataset": dem, "coordinates": [center, boundary, [129.0, 35.1]]})
    check_status(batch, 200, "다건 조회")
    check_eq([e["elevation"] is None for e in batch.data], [False, False, True], "범위 밖은 null")


@scenario("TC-QY-06", "조회", "경로 고도 프로파일: 누적 오르막/내리막", depends=["TC-RA-01"])
def qy_profile(ctx):
    start, end = ctx.api.transform([dem_pixel_center(20, 200), dem_pixel_center(220, 200)], 5186, 4326)
    r = ctx.api.post("/api/elevation/profile",
                     {"dataset": ctx.state["dem"], "coordinates": [start, end, start], "intervalM": 50})
    check_status(r, 200)
    # 동쪽으로 200픽셀(0.5m/픽셀) = 100m 상승 후 복귀
    check_close(r.data["ascentM"], 100, 0.5, "누적 오르막")
    check_close(r.data["descentM"], 100, 0.5, "누적 내리막")
    check_close(r.data["maxElevation"], 160, 0.5, "최고 고도")
    check_close(r.data["lengthM"], 2 * 200 * 30, 30, "경로 길이")


@scenario("TC-QY-07", "조회", "미배포 래스터 조회 (404)", depends=["TC-RA-06"])
def qy_unpublished(ctx):
    check_status(ctx.api.get(f"/api/rasters/{ctx.state['unpublished_raster']}/value", lon=127.0, lat=37.5), 404)
    check_status(ctx.api.get("/api/elevation", lon=127.0, lat=37.5, dataset=ctx.state["unpublished_raster"]), 404)


@scenario("TC-QY-08", "조회", "POI 반경 검색 (거리순, 카테고리 필터)", depends=["TC-VE-05"])
def qy_poi_nearby(ctx):
    r = ctx.api.get("/api/pois/nearby", lon=127.0276, lat=37.4979, radius=1200, category="subway", dataset=ctx.state["poi"])
    check_eq([p["name"] for p in r.data], ["강남역", "역삼역"], "1.2km 내 지하철역")
    check_close(r.data[1]["distanceM"], haversine(127.0276, 37.4979, 127.0364, 37.5006), 5, "역삼역 거리")
    cafes = ctx.api.get("/api/pois/nearby", lon=127.0276, lat=37.4979, radius=500, category="cafe",
                        dataset=ctx.state["poi"]).data
    check_eq([p["name"] for p in cafes], ["강남역 카페"], "카페 필터")


@scenario("TC-QY-09", "조회", "POI KNN (가장 가까운 K개)", depends=["TC-VE-05"])
def qy_poi_knn(ctx):
    r = ctx.api.get("/api/pois/nearest", lon=126.97, lat=37.55, k=2, dataset=ctx.state["poi"])
    check_eq([p["name"] for p in r.data], ["서울역", "강남역 카페"], "KNN 순서")
    distances = [p["distanceM"] for p in r.data]
    check(distances == sorted(distances), "거리 오름차순")


@scenario("TC-QY-10", "조회", "POI 검색 파라미터 검증 (400)")
def qy_poi_invalid(ctx):
    check_status(ctx.api.get("/api/pois/nearby", lon=127, lat=37.5, radius=50_000), 400, "반경 초과")
    check_status(ctx.api.get("/api/pois/nearest", lon=127, lat=37.5, k=0), 400, "k=0")
    check_status(ctx.api.get("/api/pois/nearby", lon=200, lat=37.5), 400, "경도 범위")


# ===========================================================================
# 6. 경로탐색
# ===========================================================================

@scenario("TC-RT-01", "경로", "격자 도로망 차량 경로 = 맨해튼 거리", depends=["TC-VE-01"])
def rt_grid(ctx):
    g = GRID
    far = g["lon0"] + (g["n"] - 1) * g["step"], g["lat0"] + (g["n"] - 1) * g["step"]
    r = ctx.api.route(ctx.state["grid"], (g["lon0"], g["lat0"]), far)
    check_status(r, 200)
    expected = haversine(g["lon0"], g["lat0"], far[0], g["lat0"]) + haversine(g["lon0"], g["lat0"], g["lon0"], far[1])
    check_close(r.data["distanceM"], expected, 1.0, "경로 거리")
    coords = r.data["geometry"]["coordinates"]
    check_close(coords[0][0], g["lon0"], 1e-9, "시작 경도")
    check_close(coords[-1][1], far[1], 1e-9, "끝 위도")
    # 세로(secondary, 50km/h)가 가로(residential, 30km/h)보다 빠르므로 소요시간 = 가로/30 + 세로/50
    horizontal = haversine(g["lon0"], g["lat0"], far[0], g["lat0"])
    vertical = haversine(g["lon0"], g["lat0"], g["lon0"], far[1])
    check_close(r.data["durationS"], horizontal / (30 / 3.6) + vertical / (50 / 3.6), 1.0, "소요 시간")


@scenario("TC-RT-02", "경로", "Dijkstra vs A*: 같은 최적 비용, A* 탐색 노드 감소", depends=["TC-VE-01"])
def rt_compare(ctx):
    g = GRID
    r = ctx.api.get("/api/routes/compare", fromLon=g["lon0"], fromLat=g["lat0"],
                    toLon=g["lon0"] + 15 * g["step"], toLat=g["lat0"] + 3 * g["step"], dataset=ctx.state["grid"])
    check_status(r, 200)
    check(r.data["sameCost"], "비용 동일")
    check(r.data["astar"]["settledNodes"] < r.data["dijkstra"]["settledNodes"],
          f"A* {r.data['astar']['settledNodes']} < Dijkstra {r.data['dijkstra']['settledNodes']}")
    return f"settled: dijkstra={r.data['dijkstra']['settledNodes']}, A*={r.data['astar']['settledNodes']}"


@scenario("TC-RT-03", "경로", "일방통행: 차량은 우회, 역방향 차량/보행은 직진")
def rt_oneway(ctx):
    name = ctx.new_dataset("oneway", "ROAD_NETWORK")
    ctx.api.upload_and_wait(name, ctx.path("roads_oneway.geojson"), auto_publish=True)
    check(ctx.api.route(name, sq("A"), sq("B")).data["distanceM"] > 2 * DIRECT_M, "차량 A->B 우회")
    check_close(ctx.api.route(name, sq("B"), sq("A")).data["distanceM"], DIRECT_M, 0.5, "차량 B->A 직진")
    check_close(ctx.api.route(name, sq("A"), sq("B"), mode="WALK").data["distanceM"], DIRECT_M, 0.5, "보행 직진")


@scenario("TC-RT-04", "경로", "자동차전용도로: 보행/자전거는 우회")
def rt_motorway(ctx):
    name = ctx.new_dataset("motorway", "ROAD_NETWORK")
    ctx.api.upload_and_wait(name, ctx.path("roads_motorway.geojson"), auto_publish=True)
    car = ctx.api.route(name, sq("A"), sq("B")).data
    check_close(car["distanceM"], DIRECT_M, 0.5, "차량 직진")
    check_eq(car["roads"][0]["roadClass"], "motorway", "도로 등급")
    for mode in ("WALK", "BIKE"):
        check(ctx.api.route(name, sq("A"), sq("B"), mode=mode).data["distanceM"] > 2 * DIRECT_M, f"{mode} 우회")


@scenario("TC-RT-05", "경로", "도로 폐쇄 버전 배포 시 경로 즉시 변경, 롤백 시 복구")
def rt_closure(ctx):
    name = ctx.new_dataset("closure-rt", "ROAD_NETWORK")
    ctx.api.upload_and_wait(name, ctx.path("roads_closure_v1.geojson"), auto_publish=True)
    ctx.api.upload_and_wait(name, ctx.path("roads_closure_v2.geojson"))
    check_close(ctx.api.route(name, sq("A"), sq("B")).data["distanceM"], DIRECT_M, 0.5, "v1 직진")
    check_status(ctx.api.publish(name, 2), 200)
    after = ctx.api.route(name, sq("A"), sq("B")).data
    check_eq(after["versionNo"], 2, "경로 계산 버전")
    check(after["distanceM"] > 2 * DIRECT_M, "v2 우회")
    ctx.api.post(f"/api/datasets/{name}/rollback")
    check_close(ctx.api.route(name, sq("A"), sq("B")).data["distanceM"], DIRECT_M, 0.5, "롤백 후 직진")
    ctx.state["closure_rt"] = name


@scenario("TC-RT-06", "경로", "DEM 경사 반영: 보행자는 언덕 우회, 차량은 직진")
def rt_slope(ctx):
    roads = ctx.new_dataset("hill", "ROAD_NETWORK")
    ctx.api.upload_and_wait(roads, ctx.path("roads_hill.geojson"), auto_publish=True)
    # 고도 데이터셋 이름은 애플리케이션 설정(ELEVATION_DATASET)과 같아야 한다
    ctx.api.create_dataset(ELEVATION_DATASET, "RASTER")   # 이미 있으면 409 (무시)
    job = ctx.api.upload_and_wait(ELEVATION_DATASET, ctx.path("dem_hill_4326.tif"), auto_publish=True)
    check_eq(job["versionStatus"], "PUBLISHED", f"{ELEVATION_DATASET} 배포 (generate_data.py --seed 로 체크섬 변경 필요)")

    a, b = [HILL["lon0"], HILL["lat0"]], [HILL["lon0"] + 0.004, HILL["lat0"]]
    direct = haversine(a[0], a[1], b[0], b[1])
    walk = ctx.api.route(roads, a, b, mode="WALK").data
    check(walk["distanceM"] > 2 * direct, f"보행 우회 ({walk['distanceM']:.0f}m)")
    check_close(walk["ascentM"], 0, 1e-6, "보행 누적 오르막")
    car = ctx.api.route(roads, a, b).data
    check_close(car["distanceM"], direct, 0.5, "차량 직진")
    check_close(car["ascentM"], 60, 1e-3, "차량 누적 오르막")


@scenario("TC-RT-07", "경로", "끊어진 도로망의 섬 사이 경로 없음 (404)", depends=["TC-VE-03"])
def rt_no_path(ctx):
    name = ctx.state["islands"]
    check_status(ctx.api.publish(name, 1), 200)
    check_status(ctx.api.route(name, (127.02, 37.49), (127.30, 37.60)), 404)


@scenario("TC-RT-08", "경로", "경로탐색 요청 오류 (400/404)", depends=["TC-VE-01", "TC-VE-02"])
def rt_errors(ctx):
    grid = ctx.state["grid"]
    check_status(ctx.api.route(grid, (126.5, 33.4), (127.02, 37.49)), 400, "도로에서 먼 좌표")
    check_status(ctx.api.route(grid, (127.02, 37.49), (127.021, 37.49), mode="PLANE"), 400, "잘못된 이동수단")
    check_status(ctx.api.route(ctx.state["unpublished_roads"], (127.02, 37.49), (127.021, 37.49)), 404, "미배포 도로망")
    check_status(ctx.api.route(ctx.state.get("poi", "x"), (127.02, 37.49), (127.021, 37.49)), 400, "POI 데이터셋")


# ===========================================================================
# 7. 동시성
# ===========================================================================

@scenario("TC-CC-01", "동시성", "같은 데이터셋에 10건 동시 업로드 -> 버전 번호 중복 없음")
def cc_uploads(ctx):
    name = ctx.new_dataset("concurrent", "POI")

    def upload(i):
        content = json.dumps({"type": "FeatureCollection", "features": [{
            "type": "Feature", "properties": {"name": f"동시-{i}", "category": "test"},
            "geometry": {"type": "Point", "coordinates": [127.0 + i * 0.001, 37.5]}}]}).encode()
        r = ctx.api.upload(name, content=content, filename=f"poi-{i}.geojson")
        check_status(r, 200, f"업로드 {i}")
        return r.data["jobId"]

    with concurrent.futures.ThreadPoolExecutor(10) as pool:
        job_ids = list(pool.map(upload, range(10)))
    jobs = [ctx.api.wait_job(j) for j in job_ids]
    check_eq(sorted(j["versionNo"] for j in jobs), list(range(1, 11)), "버전 번호")
    check_eq({j["versionStatus"] for j in jobs}, {"PROCESSED"}, "모두 가공 완료")


@scenario("TC-CC-02", "동시성", "배포 전환 중 동시 경로 요청 -> 오류 없음, 버전과 결과 일치", depends=["TC-RT-05"])
def cc_route_during_publish(ctx):
    name = ctx.state["closure_rt"]
    stop = time.time() + 8
    errors, results = [], []

    def worker():
        while time.time() < stop:
            r = ctx.api.route(name, sq("A"), sq("B"))
            if r.status != 200:
                errors.append(r)
                continue
            results.append((r.data["versionNo"], r.data["distanceM"]))

    with concurrent.futures.ThreadPoolExecutor(8) as pool:
        futures = [pool.submit(worker) for _ in range(8)]
        version = 1
        while time.time() < stop:
            version = 2 if version == 1 else 1
            check_status(ctx.api.publish(name, version), 200, f"v{version} 배포")
            time.sleep(0.5)
        for f in futures:
            f.result()
    ctx.api.publish(name, 1)

    check_eq(errors, [], "오류 응답")
    inconsistent = [(v, d) for v, d in results
                    if (v == 1 and abs(d - DIRECT_M) > 0.5) or (v == 2 and d < 2 * DIRECT_M)]
    check_eq(inconsistent, [], "버전과 경로가 불일치한 응답")
    versions = {v for v, _ in results}
    check_eq(versions, {1, 2}, "두 버전 모두 관측")
    return f"{len(results)}건 요청, 버전 전환 중 오류 0건"


# ===========================================================================
# 8. 장애 / 복구 (docker CLI 필요)
# ===========================================================================

def _docker(*args):
    return subprocess.run(["docker", *args], check=True, capture_output=True, text=True).stdout


def _wait_healthy(ctx, timeout=180):
    deadline = time.time() + timeout
    while time.time() < deadline:
        try:
            if ctx.api.get("/api/datasets").status == 200:
                return
        except OSError:
            pass
        time.sleep(1)
    raise AssertionError("애플리케이션이 다시 기동되지 않음")


@scenario("TC-FT-01", "장애", "가공 중 서버 재시작 -> 작업 FAILED 복구 -> 실패 단계부터 재시도", docker=True)
def ft_restart(ctx):
    name = ctx.new_dataset("big", "RASTER")
    r = ctx.api.upload(name, ctx.path("dem_big_5186.tif"))
    check_status(r, 200)
    job_id = r.data["jobId"]

    # 가공(COG 변환) 단계에 들어가면 컨테이너 재시작
    deadline = time.time() + 60
    while ctx.api.get(f"/api/jobs/{job_id}").data["stage"] != "PROCESS":
        check(time.time() < deadline, "가공 단계에 진입하지 않음")
        time.sleep(0.05)
    _docker("restart", "-t", "0", ctx.app_container)
    _wait_healthy(ctx)

    job = ctx.api.get(f"/api/jobs/{job_id}").data
    if job["status"] == "SUCCEEDED":
        return "재시작 전에 가공이 끝나 검증 불가 (generate_data.py --big-size 를 늘려 재실행)"
    check_eq(job["status"], "FAILED", "재시작 후 작업 상태")
    check("재시작" in (job["errorMessage"] or ""), f"오류 메시지: {job['errorMessage']}")
    check_eq(ctx.api.version(name, 1)["status"], "FAILED", "버전 상태")

    retry = ctx.api.post(f"/api/jobs/{job_id}/retry")
    check_status(retry, 200, "재시도")
    done = ctx.api.wait_job(retry.data["jobId"], timeout=300)
    check_eq((done["status"], done["attempt"], done["stage"]), ("SUCCEEDED", 2, "PROCESS"), "재시도 결과")
    check_eq(done["versionStatus"], "PROCESSED", "버전 상태")
    history = [h["toStatus"] for h in ctx.api.get(f"/api/datasets/{name}/versions/1/history").data]
    check_eq(history[-3:], ["FAILED", "PROCESSING", "PROCESSED"], "검수 없이 가공부터 재개")
    check_status(ctx.api.post(f"/api/jobs/{job_id}/retry"), 409, "이전 작업 재시도 거부")


@scenario("TC-FT-02", "장애", "S3 장애 시 업로드 503, DB 기반 조회는 정상, 복구 후 업로드 재개",
          depends=["TC-VE-05", "TC-VE-01"], docker=True)
def ft_storage_outage(ctx):
    # LocalStack 은 영속 볼륨이 없으므로 이 시나리오 이후 S3 데이터가 초기화된다 -> 마지막에 실행
    _docker("stop", "-t", "0", ctx.localstack_container)
    try:
        started = time.time()
        r = ctx.api.upload(ctx.state["poi"], ctx.path("pois.geojson"))
        elapsed = time.time() - started
        check_status(r, 503, "S3 장애 시 업로드")
        check(elapsed < 60, f"장애 응답 시간 {elapsed:.1f}s")
        check_status(ctx.api.get("/api/pois/nearby", lon=127.0276, lat=37.4979, dataset=ctx.state["poi"]), 200,
                     "POI 조회(DB)")
        g = GRID
        check_status(ctx.api.route(ctx.state["grid"], (g["lon0"], g["lat0"]), (g["lon0"] + 0.005, g["lat0"])), 200,
                     "경로탐색(메모리 그래프)")
    finally:
        _docker("start", ctx.localstack_container)

    deadline = time.time() + 120
    while "healthy" not in _docker("inspect", "-f", "{{.State.Health.Status}}", ctx.localstack_container):
        check(time.time() < deadline, "LocalStack 복구 실패")
        time.sleep(2)
    job = ctx.api.upload_and_wait(ctx.state["poi"], ctx.path("pois.geojson"), filename="after-recovery.geojson")
    check_eq(job["status"], "SUCCEEDED", "복구 후 업로드 파이프라인")
    return f"장애 응답 {elapsed:.1f}s"


# ===========================================================================

def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--base-url", default=os.environ.get("BASE_URL", "http://localhost:8080"))
    parser.add_argument("--data-dir", default="build/e2e-data")
    parser.add_argument("--run-id", default=time.strftime("%m%d%H%M%S"))
    parser.add_argument("--only", nargs="*", help="실행할 시나리오 ID 접두어 (예: TC-RT TC-QY-04)")
    parser.add_argument("--with-docker", action="store_true", help="재시작/스토리지 장애 시나리오 실행")
    parser.add_argument("--app-container", default="geogdal-app")
    parser.add_argument("--localstack-container", default="geogdal-localstack")
    parser.add_argument("--report", default="build/e2e-report.json")
    parser.add_argument("--verbose", action="store_true")
    args = parser.parse_args()

    ctx = Context(Api(args.base_url), args.data_dir, args.run_id, args.app_container, args.localstack_container)
    print(f"== geo-gdal-service E2E  base={args.base_url}  run={args.run_id} ==")
    started = time.time()
    results = run_all(ctx, args.only, args.with_docker, args.verbose)

    summary = {s: sum(1 for r in results if r.status == s) for s in ("PASS", "FAIL", "SKIP")}
    print(f"\n== 결과: PASS {summary['PASS']} / FAIL {summary['FAIL']} / SKIP {summary['SKIP']}"
          f"  ({time.time() - started:.0f}s) ==")
    os.makedirs(os.path.dirname(args.report) or ".", exist_ok=True)
    with open(args.report, "w", encoding="utf-8") as f:
        json.dump({"runId": args.run_id, "baseUrl": args.base_url, "summary": summary,
                   "results": [asdict(r) for r in results]}, f, ensure_ascii=False, indent=2)
    print(f"리포트: {args.report}")
    sys.exit(1 if summary["FAIL"] else 0)


if __name__ == "__main__":
    main()
