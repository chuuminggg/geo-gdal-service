# GDAL 3.6.3 네이티브 라이브러리 + Java 바인딩(libgdalalljni) + JDK 17
# libs/gdal.jar(3.6.3)와 JNI 버전을 맞추기 위해 동일 버전 이미지를 사용한다.
FROM ghcr.io/osgeo/gdal:ubuntu-full-3.6.3

# 베이스 이미지에 등록된 Apache Arrow 저장소의 서명 키가 만료되어 apt가 실패하므로 제거
RUN rm -f /etc/apt/sources.list.d/*arrow* \
    && apt-get update \
    && apt-get install -y --no-install-recommends openjdk-17-jdk-headless \
    && rm -rf /var/lib/apt/lists/*

ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
ENV PATH=$JAVA_HOME/bin:$PATH
ENV JAVA_TOOL_OPTIONS="-Djava.library.path=/usr/share/java"
