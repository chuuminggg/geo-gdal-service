# syntax=docker/dockerfile:1
# ---------- base: GDAL 3.6.3 (+ Java JNI 바인딩) + JDK 17 ----------
FROM ghcr.io/osgeo/gdal:ubuntu-full-3.6.3 AS base
# 베이스 이미지의 만료된 Apache Arrow 저장소 서명 때문에 apt 가 실패하므로 제거
RUN rm -f /etc/apt/sources.list.d/*arrow* \
    && apt-get update \
    && apt-get install -y --no-install-recommends openjdk-17-jdk-headless \
    && rm -rf /var/lib/apt/lists/*
ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
ENV PATH=$JAVA_HOME/bin:$PATH

# ---------- build ----------
FROM base AS build
WORKDIR /workspace
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
COPY libs libs
RUN --mount=type=cache,target=/root/.gradle chmod +x gradlew && ./gradlew dependencies --no-daemon -q > /dev/null
COPY src src
RUN --mount=type=cache,target=/root/.gradle ./gradlew bootJar --no-daemon -q -x test

# ---------- runtime ----------
FROM base
WORKDIR /app
COPY --from=build /workspace/build/libs/*-SNAPSHOT.jar app.jar
EXPOSE 8080
# libgdalalljni.so 위치
ENTRYPOINT ["java", "-Djava.library.path=/usr/share/java", "-jar", "/app/app.jar"]
