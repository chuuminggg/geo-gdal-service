package com.minju.geogdalservice.util;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * 작업 단위 임시 디렉터리. try-with-resources로 사용하면
 * 예외가 발생해도 작업 중 생성된 임시 파일이 모두 삭제된다.
 */
public class TempWorkspace implements AutoCloseable {

    private final Path dir;

    private TempWorkspace(Path dir) {
        this.dir = dir;
    }

    public static TempWorkspace create(String prefix) {
        try {
            return new TempWorkspace(Files.createTempDirectory(prefix));
        } catch (IOException e) {
            throw new UncheckedIOException("임시 디렉터리 생성 실패", e);
        }
    }

    public Path resolve(String fileName) {
        return dir.resolve(fileName);
    }

    public Path getDir() {
        return dir;
    }

    @Override
    public void close() {
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder())
                    .forEach(p -> p.toFile().delete());
        } catch (IOException ignored) {
            // 정리 실패는 작업 결과에 영향을 주지 않음
        }
    }
}
