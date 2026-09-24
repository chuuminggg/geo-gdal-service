"""
재현 가능한 성능 측정 (before/after 비교용)

  - 측정 전 워밍업(k6 1회, 결과 버림): JIT, 커넥션 풀, PostGIS 백엔드, GDAL 캐시를 데운다
  - k6 를 N회 반복 실행하고 API 별 p50/p95 의 '중앙값'을 결과로 사용 (단발 측정의 튐 제거)

    python scripts/perf/bench.py --label before --runs 3
    python scripts/perf/bench.py --label after  --runs 3 --compare before
"""
import argparse
import json
import os
import statistics
import subprocess
import time

SCRIPT = os.path.join(os.path.dirname(__file__), "load-test.js")


def k6(out, duration, scenario, base_url, vus):
    cmd = ["k6", "run", "--quiet", "-e", f"DURATION={duration}", "-e", f"BASE_URL={base_url}", "-e", f"VUS={vus}",
           "--summary-export", out, SCRIPT]
    if scenario:
        cmd[3:3] = ["-e", f"SCENARIO={scenario}"]
    # 임계값 실패로 k6 가 non-zero 를 반환해도 측정값은 유효하므로 계속 진행
    subprocess.run(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    metrics = json.load(open(out))["metrics"]
    result = {}
    for k, v in metrics.items():
        if k.startswith("http_req_duration{scenario:"):
            name = k[len("http_req_duration{scenario:"):-1]
            failed = metrics.get(f"http_req_failed{{scenario:{name}}}", {}).get("value", 0)
            result[name] = {"p50": v["p(50)"], "p95": v["p(95)"], "avg": v["avg"], "error_rate": failed}
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--label", required=True)
    parser.add_argument("--runs", type=int, default=3)
    parser.add_argument("--duration", default="15s")
    parser.add_argument("--vus", default="10")
    parser.add_argument("--scenario", help="쉼표로 구분 (기본: 전체)")
    parser.add_argument("--base-url", default=os.environ.get("BASE_URL", "http://localhost:8080"))
    parser.add_argument("--compare", help="비교할 이전 label")
    args = parser.parse_args()
    os.makedirs("build/perf", exist_ok=True)

    print(f"[{args.label}] 워밍업...", flush=True)
    k6("build/perf/warmup.json", "5s", args.scenario, args.base_url, args.vus)

    runs = []
    for i in range(args.runs):
        print(f"[{args.label}] 측정 {i + 1}/{args.runs}...", flush=True)
        runs.append(k6(f"build/perf/{args.label}-{i}.json", args.duration, args.scenario, args.base_url, args.vus))
        time.sleep(2)

    result = {name: {stat: statistics.median(r[name][stat] for r in runs) for stat in ("p50", "p95", "avg")}
              for name in runs[0]}
    for name in result:
        result[name]["error_rate"] = max(r[name]["error_rate"] for r in runs)
    result_path = f"build/perf/{args.label}.json"
    json.dump({"label": args.label, "runs": args.runs, "duration": args.duration, "vus": args.vus,
               "median": result, "raw": runs}, open(result_path, "w"), indent=2)

    base = json.load(open(f"build/perf/{args.compare}.json"))["median"] if args.compare else None
    header = f"{'API':<16}{'p50':>9}{'p95':>9}{'오류율':>8}"
    if base:
        header += f"{'p50 ' + args.compare:>16}{'p95 ' + args.compare:>16}{'p50 변화':>10}{'p95 변화':>10}"
    print(header)
    for name, v in sorted(result.items()):
        line = f"{name:<16}{v['p50']:>9.1f}{v['p95']:>9.1f}{v['error_rate']:>8.1%}"
        if base and name in base:
            b = base[name]
            line += (f"{b['p50']:>16.1f}{b['p95']:>16.1f}"
                     f"{(v['p50'] / b['p50'] - 1):>+10.0%}{(v['p95'] / b['p95'] - 1):>+10.0%}")
        print(line)
    print(f"-> {result_path} (단위 ms, {args.runs}회 중앙값)")
    invalid = [n for n, v in result.items() if v["error_rate"] > 0.001]
    if invalid:
        # 오류 응답은 빨리 끝나 응답시간이 좋아 보이므로 비교에 쓰면 안 된다
        print(f"!! 오류 응답이 섞인 측정은 무효: {invalid} (prepare.py 로 데이터 상태를 확인하세요)")
        raise SystemExit(1)


if __name__ == "__main__":
    main()
