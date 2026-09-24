// geo-gdal-service 부하 테스트 (k6)
//
//   python scripts/perf/prepare.py                      # 성능 테스트 데이터 배포
//   k6 run scripts/perf/load-test.js                    # 전체
//   k6 run -e SCENARIO=route_astar scripts/perf/load-test.js
//   k6 run -e BASE_URL=http://localhost:8080 -e VUS=20 scripts/perf/load-test.js
//
// 시나리오는 순서대로(startTime) 실행되며 API 별로 p95 응답시간 임계값을 둔다.
import http from 'k6/http';
import { check } from 'k6';
import { Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const VUS = parseInt(__ENV.VUS || '10');
const DURATION = __ENV.DURATION || '30s';
const ONLY = __ENV.SCENARIO;

// 성능 데이터 범위 (generate_data.py PERF_GRID / pois_perf / DEM_5186 과 동일)
const GRID = { lon0: 126.95, lat0: 37.45, step: 0.001, n: 100 };
const POI_BOX = { lon0: 126.95, lat0: 37.45, dLon: 0.15, dLat: 0.12 };
const DEM_BOX = { lon0: 127.001, lat0: 37.445, dLon: 0.13, dLat: 0.1 };

const settled = new Trend('route_settled_nodes');

const all = {
  route_astar: { exec: 'routeAstar', threshold: 'p(95)<300' },
  route_dijkstra: { exec: 'routeDijkstra', threshold: 'p(95)<500' },
  route_walk: { exec: 'routeWalk', threshold: 'p(95)<300' },
  poi_nearby: { exec: 'poiNearby', threshold: 'p(95)<100' },
  poi_nearest: { exec: 'poiNearest', threshold: 'p(95)<100' },
  elevation: { exec: 'elevation', threshold: 'p(95)<150' },
  elevation_batch: { exec: 'elevationBatch', threshold: 'p(95)<300' },
  pixel_value: { exec: 'pixelValue', threshold: 'p(95)<150' },
};

const selected = Object.entries(all).filter(([name]) => !ONLY || ONLY.split(',').includes(name));
const durationSec = parseInt(DURATION);

export const options = {
  scenarios: Object.fromEntries(selected.map(([name, s], i) => [name, {
    executor: 'constant-vus',
    exec: s.exec,
    vus: VUS,
    duration: DURATION,
    startTime: `${i * (durationSec + 2)}s`,
  }])),
  thresholds: Object.assign(
    { http_req_failed: ['rate<0.01'] },
    ...selected.map(([name, s]) => ({
      [`http_req_duration{scenario:${name}}`]: [s.threshold],
      // 시나리오별 실패율도 기록 (오류 응답은 빨라서 응답시간을 왜곡하므로 반드시 확인)
      [`http_req_failed{scenario:${name}}`]: ['rate<0.01'],
    })),
  ),
  summaryTrendStats: ['avg', 'p(50)', 'p(95)', 'p(99)', 'max'],
};

function rnd(min, span) {
  return min + Math.random() * span;
}

function gridPoint() {
  const span = (GRID.n - 1) * GRID.step;
  return [rnd(GRID.lon0, span), rnd(GRID.lat0, span)];
}

function ok(res, name) {
  check(res, { [`${name} 200`]: (r) => r.status === 200 });
}

function route(mode, algorithm) {
  const [fromLon, fromLat] = gridPoint();
  const [toLon, toLat] = gridPoint();
  const res = http.get(`${BASE_URL}/api/routes?dataset=perf-roads&mode=${mode}&algorithm=${algorithm}`
    + `&fromLon=${fromLon}&fromLat=${fromLat}&toLon=${toLon}&toLat=${toLat}`, { tags: { name: 'route' } });
  ok(res, 'route');
  if (res.status === 200) {
    settled.add(res.json('data.settledNodes'), { algorithm });
  }
}

export function routeAstar() { route('CAR', 'ASTAR'); }
export function routeDijkstra() { route('CAR', 'DIJKSTRA'); }
export function routeWalk() { route('WALK', 'ASTAR'); }

export function poiNearby() {
  const lon = rnd(POI_BOX.lon0, POI_BOX.dLon);
  const lat = rnd(POI_BOX.lat0, POI_BOX.dLat);
  ok(http.get(`${BASE_URL}/api/pois/nearby?dataset=perf-pois&lon=${lon}&lat=${lat}&radius=500&limit=50`,
    { tags: { name: 'poi_nearby' } }), 'poi_nearby');
}

export function poiNearest() {
  const lon = rnd(POI_BOX.lon0, POI_BOX.dLon);
  const lat = rnd(POI_BOX.lat0, POI_BOX.dLat);
  ok(http.get(`${BASE_URL}/api/pois/nearest?dataset=perf-pois&lon=${lon}&lat=${lat}&k=10`,
    { tags: { name: 'poi_nearest' } }), 'poi_nearest');
}

export function elevation() {
  const lon = rnd(DEM_BOX.lon0, DEM_BOX.dLon);
  const lat = rnd(DEM_BOX.lat0, DEM_BOX.dLat);
  ok(http.get(`${BASE_URL}/api/elevation?dataset=perf-dem&lon=${lon}&lat=${lat}`,
    { tags: { name: 'elevation' } }), 'elevation');
}

export function elevationBatch() {
  const coordinates = Array.from({ length: 100 },
    () => [rnd(DEM_BOX.lon0, DEM_BOX.dLon), rnd(DEM_BOX.lat0, DEM_BOX.dLat)]);
  ok(http.post(`${BASE_URL}/api/elevation/batch`, JSON.stringify({ dataset: 'perf-dem', coordinates }),
    { headers: { 'Content-Type': 'application/json' }, tags: { name: 'elevation_batch' } }), 'elevation_batch');
}

export function pixelValue() {
  const lon = rnd(DEM_BOX.lon0, DEM_BOX.dLon);
  const lat = rnd(DEM_BOX.lat0, DEM_BOX.dLat);
  ok(http.get(`${BASE_URL}/api/rasters/perf-dem/value?lon=${lon}&lat=${lat}`,
    { tags: { name: 'pixel_value' } }), 'pixel_value');
}
