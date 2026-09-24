#!/usr/bin/env bash
# 시스템 테스트 일괄 실행
#   1) 전체 스택 기동 (PostGIS + LocalStack + App)   2) 테스트 데이터 생성
#   3) E2E 시나리오 (장애 시나리오 포함)             4) 성능 테스트 (k6, 선택)
#
#   bash scripts/run-system-tests.sh            # E2E 만
#   bash scripts/run-system-tests.sh --perf     # E2E + 성능
set -euo pipefail
cd "$(dirname "$0")/.."
export MSYS_NO_PATHCONV=1 PYTHONIOENCODING=utf-8
BASE_URL=${BASE_URL:-http://localhost:8080}

echo "== 1. 스택 기동"
docker build -q -t geogdal-gdal docker/gdal > /dev/null
docker compose --profile app up -d --build
for i in $(seq 1 90); do
  curl -sf "$BASE_URL/api/datasets" > /dev/null && break
  [ "$i" = 90 ] && { echo "애플리케이션 기동 실패"; docker logs --tail 50 geogdal-app; exit 1; }
  sleep 2
done

echo "== 2. 테스트 데이터 생성"
docker run --rm -v "$PWD":/src geogdal-gdal \
  python3 /src/scripts/e2e/generate_data.py /src/build/e2e-data --seed "$(date +%s)" | tail -1

echo "== 3. E2E 시나리오"
python scripts/e2e/run_scenarios.py --base-url "$BASE_URL" --with-docker

if [ "${1:-}" = "--perf" ]; then
  echo "== 4. 성능 테스트"
  # E2E 의 스토리지 장애 시나리오(TC-FT-02)가 S3 를 초기화하므로, 조회 가능 여부를 확인하고 필요하면 다시 적재
  python scripts/perf/prepare.py --base-url "$BASE_URL"
  python scripts/perf/compare_algorithms.py --base-url "$BASE_URL" --pairs 300
  # 워밍업 후 3회 반복, API 별 중앙값 (오류 응답이 섞이면 실패 처리)
  python scripts/perf/bench.py --base-url "$BASE_URL" --label latest --runs 3
fi
