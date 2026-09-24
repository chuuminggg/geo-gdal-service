"""
성능 테스트용 데이터셋 준비 (이미 배포되어 있으면 건너뜀)

    python scripts/perf/prepare.py --base-url http://localhost:8080 --data-dir build/e2e-data

  perf-roads : 100 x 100 격자 도로망 (노드 10,000 / 링크 19,800)
  perf-pois  : POI 20,000건
  perf-dem   : 서울 DEM (EPSG:5186, 400 x 400, 30m)
"""
import argparse
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "e2e"))
from api import Api, check  # noqa: E402

DATASETS = [
    ("perf-roads", "ROAD_NETWORK", "roads_perf.geojson"),
    ("perf-pois", "POI", "pois_perf.geojson"),
    ("perf-dem", "RASTER", "dem_seoul_5186.tif"),
]

# 배포 상태뿐 아니라 실제로 조회가 되는지 확인 (예: LocalStack 재시작으로 S3 객체가 사라진 경우)
PROBES = {
    "perf-roads": ("/api/routes", dict(fromLon=126.95, fromLat=37.45, toLon=126.96, toLat=37.46, dataset="perf-roads")),
    "perf-pois": ("/api/pois/nearest", dict(lon=127.0, lat=37.5, k=1, dataset="perf-pois")),
    "perf-dem": ("/api/elevation", dict(lon=127.05, lat=37.5, dataset="perf-dem")),
}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default=os.environ.get("BASE_URL", "http://localhost:8080"))
    parser.add_argument("--data-dir", default="build/e2e-data")
    args = parser.parse_args()
    api = Api(args.base_url, timeout=300)

    for name, dtype, file in DATASETS:
        existing = api.get(f"/api/datasets/{name}")
        probe_path, probe_params = PROBES[name]
        if existing.status == 200 and existing.data["activeVersionNo"] is not None:
            probe = api.get(probe_path, **probe_params)
            if probe.status == 200:
                print(f"{name}: 이미 배포되어 조회 가능 (v{existing.data['activeVersionNo']})")
                continue
            print(f"{name}: 배포되어 있지만 조회 실패({probe.status}) -> 다시 업로드")
        if existing.status == 404:
            check(api.create_dataset(name, dtype, "성능 테스트").status == 200, f"{name} 생성 실패")
        job = api.upload_and_wait(name, os.path.join(args.data_dir, file), auto_publish=True)
        check(job["versionStatus"] == "PUBLISHED", f"{name} 배포 실패: {job}")
        print(f"{name}: v{job['versionNo']} 배포 완료")
        probe = api.get(probe_path, **probe_params)
        check(probe.status == 200, f"{name} 조회 확인 실패: {probe}")
    print("준비 완료")


if __name__ == "__main__":
    main()
