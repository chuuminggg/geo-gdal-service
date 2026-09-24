"""
시스템 테스트용 공간 데이터 생성기 (GDAL/OGR Python 바인딩 필요 -> geogdal-gdal 이미지에서 실행)

    docker run --rm -v "$PWD":/src geogdal-gdal python3 /src/scripts/e2e/generate_data.py /src/build/e2e-data

모든 데이터는 결정적(deterministic)이라 시나리오에서 기대값을 정확히 계산할 수 있다.
단, --seed 로 일부 파일(DEM 등)의 한 픽셀 값을 바꿔 체크섬이 달라지게 할 수 있다 (재실행 시 중복 업로드 방지).
"""
import argparse
import json
import math
import os
import random

import numpy as np
from osgeo import gdal, osr

gdal.UseExceptions()

# ---------------------------------------------------------------------------
# 좌표 기준값 (시나리오 스크립트 constants.py 와 동일해야 함)
# ---------------------------------------------------------------------------
DEM_5186 = dict(origin_x=200_000, origin_y=550_000, pixel=30, width=400, height=400)   # 서울 (경도 127.0, 위도 ~37.55)
GRID = dict(lon0=127.020, lat0=37.490, step=0.001, n=20)                             # 강남 일대 20x20 격자 (~100m)
SQUARE = dict(lon0=127.100, lat0=37.500, dx=0.004, dy=0.002)                           # 일방통행/자동차전용/폐쇄 검증용 사각형
HILL = dict(lon0=127.200, lat0=37.500)                                                 # 경사 우회 검증용
PERF_GRID = dict(lon0=126.950, lat0=37.450, step=0.001, n=100)                         # 성능 테스트용 100x100 격자


def write_tif(path, width, height, epsg, geotransform, values, nodata=None, dtype=gdal.GDT_Float32):
    ds = gdal.GetDriverByName("GTiff").Create(path, width, height, 1, dtype, options=["COMPRESS=DEFLATE", "TILED=YES"])
    ds.SetGeoTransform(geotransform)
    if epsg is not None:
        srs = osr.SpatialReference()
        srs.ImportFromEPSG(epsg)
        ds.SetProjection(srs.ExportToWkt())
    band = ds.GetRasterBand(1)
    if nodata is not None:
        band.SetNoDataValue(nodata)
    # values(cols, rows): numpy 배열(열 인덱스, 행 인덱스)을 받아 값 배열을 반환. 512행씩 나눠 쓴다
    cols = np.arange(width)
    for row0 in range(0, height, 512):
        rows = np.arange(row0, min(height, row0 + 512))
        cc, rr = np.meshgrid(cols, rows)
        band.WriteArray(np.asarray(values(cc, rr), dtype=np.float32), 0, row0)
    ds.FlushCache()
    ds = None


def feature(geometry, **props):
    return {"type": "Feature", "properties": props, "geometry": geometry}


def line(coords, **props):
    return feature({"type": "LineString", "coordinates": coords}, **props)


def point(lon, lat, **props):
    return feature({"type": "Point", "coordinates": [lon, lat]}, **props)


def write_geojson(path, features):
    with open(path, "w", encoding="utf-8") as f:
        json.dump({"type": "FeatureCollection", "features": features}, f, ensure_ascii=False)


def grid_roads(lon0, lat0, step, n, name_prefix="격자로"):
    """n x n 격자. 가로(동서)는 residential 30km/h, 세로(남북)는 secondary 50km/h"""
    features = []
    link_id = 1
    for row in range(n):
        for col in range(n):
            lon = round(lon0 + col * step, 7)
            lat = round(lat0 + row * step, 7)
            if col < n - 1:
                features.append(line([[lon, lat], [round(lon + step, 7), lat]],
                                     link_id=str(link_id), highway="residential", maxspeed="30",
                                     name=f"{name_prefix} 가로{row}"))
                link_id += 1
            if row < n - 1:
                features.append(line([[lon, lat], [lon, round(lat + step, 7)]],
                                     link_id=str(link_id), highway="secondary", maxspeed="50",
                                     name=f"{name_prefix} 세로{col}"))
                link_id += 1
    return features


def square(direct_props, include_direct=True):
    """A(좌하) - B(우하) 직선 + 북쪽 우회로 A - C - D - B"""
    x0, y0, dx, dy = SQUARE["lon0"], SQUARE["lat0"], SQUARE["dx"], SQUARE["dy"]
    a, b, c, d = [x0, y0], [x0 + dx, y0], [x0, y0 + dy], [x0 + dx, y0 + dy]
    features = [
        line([a, c], link_id="2", highway="primary", maxspeed="50", name="우회로"),
        line([c, d], link_id="3", highway="primary", maxspeed="50", name="우회로"),
        line([d, b], link_id="4", highway="primary", maxspeed="50", name="우회로"),
    ]
    if include_direct:
        features.append(line([a, b], link_id="1", **direct_props))
    return features


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("out")
    parser.add_argument("--seed", type=int, default=0,
                        help="실행마다 다른 값(예: 현재 시각)을 주면 DEM 파일 체크섬이 달라져 재실행 시 중복 업로드로 거부되지 않는다")
    parser.add_argument("--big-size", type=int, default=6000, help="재시작 시나리오용 큰 래스터 한 변 픽셀 수")
    args = parser.parse_args()
    out = args.out
    os.makedirs(out, exist_ok=True)
    seed = args.seed
    # 결과에 영향을 주지 않는 모서리 픽셀 한 개에만 반영 (0 < tweak < 1)
    tweak = ((seed % 999_983) + 1) / 1_000_000

    # ---------------- 래스터 ----------------
    d = DEM_5186
    gt_5186 = (d["origin_x"], d["pixel"], 0, d["origin_y"], 0, -d["pixel"])
    # 고도 = 50 + 0.5 * col (동쪽으로 갈수록 상승), 좌상단 10x10 NoData, 우하단 한 픽셀에 seed 반영(최소값 50 은 유지)
    def dem_value(c, r):
        v = 50 + 0.5 * c
        v = np.where((c < 10) & (r < 10), -9999, v)
        return np.where((c == d["width"] - 1) & (r == d["height"] - 1), v + tweak, v)

    write_tif(f"{out}/dem_seoul_5186.tif", d["width"], d["height"], 5186, gt_5186, dem_value, nodata=-9999)
    write_tif(f"{out}/dem_nocrs.tif", 50, 50, None, (0, 1, 0, 0, 0, 1), lambda c, r: c * 0 + 1)
    write_tif(f"{out}/dem_tokyo_4326.tif", 100, 100, 4326, (139.70, 0.0001, 0, 35.70, 0, -0.0001),
              lambda c, r: c * 0 + 10 + tweak)
    # NoData 70% -> 경고(WARN)지만 통과
    write_tif(f"{out}/dem_sparse_5186.tif", 100, 100, 5186, gt_5186,
              lambda c, r: np.where(c < 70, -9999, 100 + tweak), nodata=-9999)
    with open(f"{out}/corrupted.tif", "w") as f:
        f.write("this is not a tiff file")
    with open(f"{out}/image.png", "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n")

    # 경사 반영 경로탐색용 DEM (EPSG:4326): HILL 사각형의 가운데 노드 주변만 60m 언덕
    hx, hy = HILL["lon0"], HILL["lat0"]
    ox, oy, px = hx - 0.001, hy + 0.003, 0.0001
    hill_col = round((hx + 0.002 - ox) / px)
    hill_row = round((oy - hy) / px)
    write_tif(f"{out}/dem_hill_4326.tif", 60, 50, 4326, (ox, px, 0, oy, 0, -px),
              lambda c, r: np.where((abs(c - hill_col) <= 2) & (r >= hill_row - 5), 60,
                                    np.where((c == 0) & (r == 0), tweak, 0)))

    # 재시작 시나리오용 큰 래스터 (COG 변환에 수 초 이상 걸리도록)
    n = args.big_size
    write_tif(f"{out}/dem_big_5186.tif", n, n, 5186, (180_000, 5, 0, 560_000, 0, -5),
              lambda c, r: ((c * 7 + r * 13) % 1000) / 10.0 + np.where((c == 0) & (r == 0), tweak, 0))

    # ---------------- 도로 네트워크 ----------------
    g = GRID
    write_geojson(f"{out}/roads_grid.geojson", grid_roads(g["lon0"], g["lat0"], g["step"], g["n"]))
    # 일방통행: 직선 A->B 가 B->A 방향 일방통행 (oneway=-1)
    write_geojson(f"{out}/roads_oneway.geojson", square({"highway": "primary", "maxspeed": "50", "oneway": "-1", "name": "일방로"}))
    # 자동차전용도로 직선
    write_geojson(f"{out}/roads_motorway.geojson", square({"highway": "motorway", "maxspeed": "100", "name": "도시고속로"}))
    # 폐쇄 시나리오: v1 = 직선 포함, v2 = 직선 폐쇄
    write_geojson(f"{out}/roads_closure_v1.geojson", square({"highway": "primary", "maxspeed": "50", "name": "직선로"}))
    write_geojson(f"{out}/roads_closure_v2.geojson", square({}, include_direct=False))
    # 언덕: A - H - B 직선 + 평지 우회
    hill = [
        line([[hx, hy], [hx + 0.002, hy]], link_id="1", highway="residential", name="언덕길"),
        line([[hx + 0.002, hy], [hx + 0.004, hy]], link_id="2", highway="residential", name="언덕길"),
        line([[hx, hy], [hx, hy + 0.002]], link_id="3", highway="residential", name="평지길"),
        line([[hx, hy + 0.002], [hx + 0.004, hy + 0.002]], link_id="4", highway="residential", name="평지길"),
        line([[hx + 0.004, hy + 0.002], [hx + 0.004, hy]], link_id="5", highway="residential", name="평지길"),
    ]
    write_geojson(f"{out}/roads_hill.geojson", hill)
    # 끊어진 도로망: 격자 + 멀리 떨어진 섬 여러 개 -> ROAD_CONNECTIVITY 경고
    islands = grid_roads(127.30, 37.60, 0.001, 4, "섬A") + grid_roads(127.40, 37.60, 0.001, 4, "섬B")
    write_geojson(f"{out}/roads_disconnected.geojson", grid_roads(127.02, 37.49, 0.001, 4) + islands)
    # 도로 데이터셋에 포인트만 -> GEOMETRY_TYPE 불합격
    write_geojson(f"{out}/roads_points.geojson", [point(127.0, 37.5, name="점")])
    # Shapefile(zip, EPSG:5186): /vsizip + 좌표계 변환 경로 검증
    shp_dir = f"{out}/roads_shp"
    os.makedirs(shp_dir, exist_ok=True)
    gdal.VectorTranslate(f"{shp_dir}/roads.shp", f"{out}/roads_grid.geojson",
                         options=gdal.VectorTranslateOptions(format="ESRI Shapefile", dstSRS="EPSG:5186",
                                                             layerCreationOptions=["ENCODING=UTF-8"]))
    import zipfile
    with zipfile.ZipFile(f"{out}/roads_grid_5186_shp.zip", "w") as z:
        for fn in os.listdir(shp_dir):
            z.write(f"{shp_dir}/{fn}", fn)

    # ---------------- POI ----------------
    pois = [
        point(127.0276, 37.4979, name="강남역", category="subway", address="서울 강남구 강남대로 396"),
        point(127.0364, 37.5006, name="역삼역", category="subway", address="서울 강남구 테헤란로 156"),
        point(127.0490, 37.5045, name="선릉역", category="subway", address="서울 강남구 테헤란로 340"),
        point(127.0280, 37.4985, name="강남역 카페", category="cafe"),
        point(127.0290, 37.4990, name="강남역 편의점", category="convenience"),
        point(126.9707, 37.5547, name="서울역", category="subway", address="서울 중구 한강대로 405"),
    ]
    write_geojson(f"{out}/pois.geojson", pois)
    write_geojson(f"{out}/pois_noname.geojson",
                  [point(127.0 + i * 0.001, 37.5, category="cafe") for i in range(3)] + [point(127.0, 37.51, name="이름있음")])
    write_geojson(f"{out}/pois_tokyo.geojson", [point(139.767, 35.681, name="東京駅", category="subway")])

    # ---------------- 성능 테스트 데이터 ----------------
    p = PERF_GRID
    write_geojson(f"{out}/roads_perf.geojson", grid_roads(p["lon0"], p["lat0"], p["step"], p["n"], "성능"))
    rnd = random.Random(42)
    categories = ["cafe", "restaurant", "subway", "bank", "convenience"]
    write_geojson(f"{out}/pois_perf.geojson", [
        point(round(126.95 + rnd.random() * 0.15, 7), round(37.45 + rnd.random() * 0.12, 7),
              name=f"POI-{i}", category=categories[i % len(categories)])
        for i in range(20_000)])

    print("generated:", sorted(f for f in os.listdir(out) if os.path.isfile(f"{out}/{f}")))


if __name__ == "__main__":
    main()
