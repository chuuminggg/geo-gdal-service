"""
geo-gdal-service HTTP 클라이언트 + 시나리오 실행 프레임워크 (표준 라이브러리만 사용)
"""
import json
import math
import mimetypes
import time
import traceback
import urllib.error
import urllib.parse
import urllib.request
import uuid
from dataclasses import dataclass, field


class Response:
    def __init__(self, status, body):
        self.status = status
        self.body = body

    @property
    def data(self):
        return self.body.get("data") if isinstance(self.body, dict) else None

    def __repr__(self):
        return f"<HTTP {self.status} {json.dumps(self.body, ensure_ascii=False)[:300]}>"


class Api:
    def __init__(self, base_url, timeout=120):
        self.base_url = base_url.rstrip("/")
        self.timeout = timeout

    def request(self, method, path, params=None, json_body=None, files=None, fields=None):
        url = self.base_url + path
        if params:
            url += "?" + urllib.parse.urlencode({k: v for k, v in params.items() if v is not None})
        headers = {"Accept": "application/json"}
        data = None
        if json_body is not None:
            data = json.dumps(json_body).encode("utf-8")
            headers["Content-Type"] = "application/json"
        elif files:
            data, content_type = _multipart(files, fields or {})
            headers["Content-Type"] = content_type
        req = urllib.request.Request(url, data=data, method=method, headers=headers)
        try:
            with urllib.request.urlopen(req, timeout=self.timeout) as resp:
                return Response(resp.status, _parse(resp.read()))
        except urllib.error.HTTPError as e:
            return Response(e.code, _parse(e.read()))

    def get(self, path, **params):
        return self.request("GET", path, params=params)

    def post(self, path, json_body=None, params=None):
        return self.request("POST", path, params=params, json_body=json_body)

    # ---------- 도메인 헬퍼 ----------

    def create_dataset(self, name, dtype, description=None):
        return self.post("/api/datasets", {"name": name, "type": dtype, "description": description})

    def upload(self, dataset, file_path=None, content=None, filename=None, auto_publish=False):
        if content is None:
            with open(file_path, "rb") as f:
                content = f.read()
            filename = filename or file_path.replace("\\", "/").split("/")[-1]
        return self.request("POST", f"/api/datasets/{dataset}/versions",
                            files={"file": (filename, content)},
                            fields={"autoPublish": str(auto_publish).lower()})

    def wait_job(self, job_id, timeout=120, interval=0.3):
        deadline = time.time() + timeout
        while True:
            r = self.get(f"/api/jobs/{job_id}")
            if r.status == 200 and r.data["status"] in ("SUCCEEDED", "FAILED"):
                return r.data
            if time.time() > deadline:
                raise AssertionError(f"작업 {job_id} 이 {timeout}초 안에 끝나지 않음: {r}")
            time.sleep(interval)

    def upload_and_wait(self, dataset, file_path=None, auto_publish=False, **kwargs):
        r = self.upload(dataset, file_path, auto_publish=auto_publish, **kwargs)
        check(r.status == 200, f"업로드 실패: {r}")
        return self.wait_job(r.data["jobId"])

    def version(self, dataset, no):
        r = self.get(f"/api/datasets/{dataset}/versions/{no}")
        check(r.status == 200, f"버전 조회 실패: {r}")
        return r.data

    def validation(self, dataset, no):
        return self.get(f"/api/datasets/{dataset}/versions/{no}/validation").data

    def failed_rules(self, dataset, no, severity=None):
        return [v["ruleCode"] for v in self.validation(dataset, no)
                if not v["passed"] and (severity is None or v["severity"] == severity)]

    def publish(self, dataset, no):
        return self.post(f"/api/datasets/{dataset}/versions/{no}/publish")

    def route(self, dataset, frm, to, mode="CAR", algorithm="ASTAR"):
        return self.get("/api/routes", fromLon=frm[0], fromLat=frm[1], toLon=to[0], toLat=to[1],
                        mode=mode, algorithm=algorithm, dataset=dataset)

    def transform(self, coords, src, dst):
        r = self.post("/api/coordinates/transform", {"from": src, "to": dst, "coordinates": coords})
        check(r.status == 200, f"좌표 변환 실패: {r}")
        return r.data["coordinates"]


def _parse(raw):
    try:
        return json.loads(raw.decode("utf-8")) if raw else None
    except (ValueError, UnicodeDecodeError):
        return {"raw": raw[:200].decode("utf-8", "replace")}


def _multipart(files, fields):
    boundary = uuid.uuid4().hex
    parts = []
    for name, value in fields.items():
        parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="{name}"\r\n\r\n{value}\r\n'.encode())
    for name, (filename, content) in files.items():
        ctype = mimetypes.guess_type(filename)[0] or "application/octet-stream"
        parts.append((f'--{boundary}\r\nContent-Disposition: form-data; name="{name}"; filename="{filename}"\r\n'
                      f"Content-Type: {ctype}\r\n\r\n").encode("utf-8") + content + b"\r\n")
    parts.append(f"--{boundary}--\r\n".encode())
    return b"".join(parts), f"multipart/form-data; boundary={boundary}"


# ---------------------------------------------------------------------------
# 검증 헬퍼
# ---------------------------------------------------------------------------

def check(condition, message):
    if not condition:
        raise AssertionError(message)


def check_eq(actual, expected, label):
    check(actual == expected, f"{label}: 기대 {expected!r}, 실제 {actual!r}")


def check_close(actual, expected, tol, label):
    check(actual is not None and abs(actual - expected) <= tol,
          f"{label}: 기대 {expected} ± {tol}, 실제 {actual}")


def check_status(resp, expected, label="HTTP 상태"):
    check(resp.status == expected, f"{label}: 기대 {expected}, 실제 {resp}")


def haversine(lon1, lat1, lon2, lat2):
    r = 6_371_008.8
    d_lat = math.radians(lat2 - lat1)
    d_lon = math.radians(lon2 - lon1)
    a = (math.sin(d_lat / 2) ** 2
         + math.cos(math.radians(lat1)) * math.cos(math.radians(lat2)) * math.sin(d_lon / 2) ** 2)
    return 2 * r * math.asin(min(1.0, math.sqrt(a)))


# ---------------------------------------------------------------------------
# 시나리오 레지스트리 / 실행기
# ---------------------------------------------------------------------------

@dataclass
class Scenario:
    id: str
    group: str
    title: str
    func: callable
    depends: list = field(default_factory=list)
    docker: bool = False


@dataclass
class Result:
    id: str
    group: str
    title: str
    status: str   # PASS / FAIL / SKIP
    seconds: float
    message: str = ""


REGISTRY = []


def scenario(sid, group, title, depends=(), docker=False):
    def decorator(func):
        REGISTRY.append(Scenario(sid, group, title, func, list(depends), docker))
        return func
    return decorator


def run_all(ctx, only=None, with_docker=False, verbose=False):
    results = {}
    ordered = []
    for sc in REGISTRY:
        if only and not any(sc.id.startswith(p) for p in only):
            continue
        start = time.time()
        failed_dep = next((d for d in sc.depends if d in results and results[d].status != "PASS"), None)
        if sc.docker and not with_docker:
            res = Result(sc.id, sc.group, sc.title, "SKIP", 0, "--with-docker 옵션 필요")
        elif failed_dep:
            res = Result(sc.id, sc.group, sc.title, "SKIP", 0, f"선행 시나리오 {failed_dep} 실패")
        else:
            try:
                note = sc.func(ctx)
                res = Result(sc.id, sc.group, sc.title, "PASS", time.time() - start, note or "")
            except AssertionError as e:
                res = Result(sc.id, sc.group, sc.title, "FAIL", time.time() - start, str(e))
            except Exception as e:  # 예상치 못한 오류도 실패로 기록하고 계속 진행
                msg = f"{type(e).__name__}: {e}"
                if verbose:
                    msg += "\n" + traceback.format_exc()
                res = Result(sc.id, sc.group, sc.title, "FAIL", time.time() - start, msg)
        results[sc.id] = res
        ordered.append(res)
        mark = {"PASS": "\033[32mPASS\033[0m", "FAIL": "\033[31mFAIL\033[0m", "SKIP": "\033[33mSKIP\033[0m"}[res.status]
        print(f"[{mark}] {res.id:<10} {res.title} ({res.seconds:.1f}s)" + (f"\n         -> {res.message}" if res.message else ""),
              flush=True)
    return ordered
