#!/bin/bash
# GDAL 네이티브 라이브러리가 필요한 테스트를 컨테이너에서 실행한다.
#
#   docker build -t geogdal-gdal docker/gdal
#   docker run --rm -v "$PWD":/src:ro -v geogdal-gradle:/root/.gradle \
#     -v /var/run/docker.sock:/var/run/docker.sock \
#     -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
#     geogdal-gdal bash /src/docker/gdal/run-tests.sh
#
# docker.sock 을 마운트하면 Testcontainers(PostGIS, LocalStack) 기반 E2E 테스트까지 실행된다.
# (마운트하지 않으면 Docker 가 필요한 테스트는 자동으로 건너뛴다)
mkdir -p /work
cd /src && tar --exclude=./build --exclude=./.gradle --exclude=./.idea -cf - . | (cd /work && tar -xf -)
cd /work
./gradlew test --no-daemon --console=plain -q "$@"
status=$?

echo "===== test summary ====="
for f in build/test-results/test/*.xml; do
  [ -f "$f" ] || continue
  name=$(basename "$f" .xml | sed 's/^TEST-//')
  counts=$(grep -o 'tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*" errors="[0-9]*"' "$f" | head -1)
  echo "$name $counts"
done
grep -h -A3 '<failure' build/test-results/test/*.xml 2>/dev/null | head -60
exit $status
