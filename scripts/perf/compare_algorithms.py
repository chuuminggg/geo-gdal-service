"""
Dijkstra vs A* 비교 벤치마크 (/api/routes/compare 의 서버 측 탐색 시간/확정 노드 수 사용)

    python scripts/perf/compare_algorithms.py --pairs 300 --dataset perf-roads

- 모든 쌍에서 두 알고리즘의 최소 비용이 같은지(A* 최적성) 검증
- 출발-도착 직선거리 구간별로 확정 노드 수와 탐색 시간을 비교
"""
import argparse
import os
import random
import statistics
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "e2e"))
from api import Api, haversine  # noqa: E402

GRID = dict(lon0=126.95, lat0=37.45, span=0.099)
BUCKETS = [(0, 2000), (2000, 5000), (5000, 20000)]


def pct(values, p):
    values = sorted(values)
    return values[min(len(values) - 1, int(len(values) * p))]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default=os.environ.get("BASE_URL", "http://localhost:8080"))
    parser.add_argument("--dataset", default="perf-roads")
    parser.add_argument("--pairs", type=int, default=300)
    parser.add_argument("--mode", default="CAR")
    parser.add_argument("--seed", type=int, default=7)
    args = parser.parse_args()
    api = Api(args.base_url)
    rnd = random.Random(args.seed)

    rows = []
    for _ in range(args.pairs):
        a = (GRID["lon0"] + rnd.random() * GRID["span"], GRID["lat0"] + rnd.random() * GRID["span"])
        b = (GRID["lon0"] + rnd.random() * GRID["span"], GRID["lat0"] + rnd.random() * GRID["span"])
        r = api.get("/api/routes/compare", fromLon=a[0], fromLat=a[1], toLon=b[0], toLat=b[1],
                    mode=args.mode, dataset=args.dataset)
        if r.status != 200:
            continue
        d = r.data
        rows.append(dict(dist=haversine(*a, *b), same=d["sameCost"],
                         d_nodes=d["dijkstra"]["settledNodes"], a_nodes=d["astar"]["settledNodes"],
                         d_ms=d["dijkstra"]["elapsedMs"], a_ms=d["astar"]["elapsedMs"]))

    mismatched = [r for r in rows if not r["same"]]
    print(f"경로 {len(rows)}쌍, 최적 비용 불일치 {len(mismatched)}건 ({args.mode})")
    print(f"{'직선거리':>14} | {'쌍':>4} | {'확정노드 Dijkstra':>16} | {'A*':>8} | {'비율':>6} | "
          f"{'시간(ms) Dijkstra p50/p95':>24} | {'A* p50/p95':>14}")
    for lo, hi in BUCKETS + [(0, 10 ** 9)]:
        b = [r for r in rows if lo <= r["dist"] < hi]
        if not b:
            continue
        label = "전체" if hi == 10 ** 9 else f"{lo / 1000:.0f}~{hi / 1000:.0f}km"
        dn = statistics.mean(r["d_nodes"] for r in b)
        an = statistics.mean(r["a_nodes"] for r in b)
        dms = [r["d_ms"] for r in b]
        ams = [r["a_ms"] for r in b]
        print(f"{label:>14} | {len(b):>4} | {dn:>16.0f} | {an:>8.0f} | {an / dn:>5.0%} | "
              f"{pct(dms, .5):>11.2f} / {pct(dms, .95):>6.2f} | {pct(ams, .5):>6.2f} / {pct(ams, .95):>5.2f}")
    sys.exit(1 if mismatched else 0)


if __name__ == "__main__":
    main()
