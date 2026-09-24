package com.minju.geogdalservice.gis;

import com.minju.geogdalservice.entity.DatasetType;
import com.minju.geogdalservice.pipeline.PipelineEvents;
import com.minju.geogdalservice.support.GdalTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@EnabledIf("com.minju.geogdalservice.support.GdalTestSupport#gdalAvailable")
class RasterHandlePoolGdalTest {

    @TempDir
    Path tempDir;

    private Path dem(String name) throws Exception {
        // 풀은 경로 문자열로 구분하므로 배포 경로 형식(/processed/{dataset}/v1/...)을 흉내낸다
        Path dir = Files.createDirectories(tempDir.resolve("processed").resolve(name).resolve("v1"));
        // EPSG:4326, 원점 (127.0, 37.6), 0.001도, 값 = col
        return GdalTestSupport.createGeoTiff(dir.resolve("dem.tif"), 4326, 127.0, 37.6, 0.001,
                100, 100, (col, row) -> col, null);
    }

    @Test
    void 반납된_핸들을_재사용하고_경로당_유휴_핸들_수를_제한한다() throws Exception {
        String path = dem("seoul").toString();
        RasterHandlePool pool = new RasterHandlePool(GdalTestSupport.initializer(), 2);

        OpenRaster first = pool.borrow(path);
        first.close();
        OpenRaster second = pool.borrow(path);
        assertThat(second).isSameAs(first);   // 재사용
        second.close();
        assertThat(pool.openedCount()).isEqualTo(1);
        assertThat(pool.reusedCount()).isEqualTo(1);

        // 동시에 3개를 빌리면 새로 열고, 반납 시 최대 2개만 유휴로 남긴다
        List<OpenRaster> handles = List.of(pool.borrow(path), pool.borrow(path), pool.borrow(path));
        handles.forEach(OpenRaster::close);
        assertThat(pool.openedCount()).isEqualTo(3);
        pool.borrow(path).close();
        pool.borrow(path).close();
        assertThat(pool.openedCount()).isEqualTo(3);
    }

    @Test
    void 래스터가_새로_배포되면_이전_경로의_유휴_핸들을_닫고_이후_반납분도_닫는다() throws Exception {
        String path = dem("seoul").toString();
        RasterHandlePool pool = new RasterHandlePool(GdalTestSupport.initializer(), 4);
        pool.borrow(path).close();
        OpenRaster inUse = pool.borrow(path);
        pool.borrow(path).close();

        pool.onPublished(new PipelineEvents.DatasetPublished(1L, "seoul", DatasetType.RASTER, 2L));
        inUse.close();   // 교체된 경로의 핸들은 풀에 돌아가지 않고 닫힘

        long before = pool.openedCount();
        pool.borrow(path).close();
        assertThat(pool.openedCount()).isEqualTo(before + 1);   // 유휴 핸들이 없으므로 새로 연다
        // 롤백 등으로 다시 쓰이기 시작한 경로는 다시 풀링된다
        pool.borrow(path).close();
        assertThat(pool.openedCount()).isEqualTo(before + 1);
    }

    @Test
    void 여러_스레드가_동시에_빌려도_값이_정확하다() throws Exception {
        String path = dem("concurrent").toString();
        RasterHandlePool pool = new RasterHandlePool(GdalTestSupport.initializer(), 8);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < 8; t++) {
                int seed = t;
                futures.add(executor.submit(() -> {
                    for (int i = 0; i < 500; i++) {
                        int col = (seed * 31 + i * 7) % 99;
                        try (OpenRaster raster = pool.borrow(path)) {
                            // 픽셀 col, col+1 중심 사이 -> col + 0.5
                            double lon = 127.0 + 0.001 * (col + 1.0);
                            assertThat(raster.interpolate(lon, 37.55)).isCloseTo(col + 0.5, within(1e-6));
                        }
                    }
                    return null;
                }));
            }
            for (Future<?> f : futures) {
                f.get();
            }
        } finally {
            executor.shutdown();
        }
        assertThat(pool.openedCount()).isLessThanOrEqualTo(8);
        assertThat(pool.reusedCount()).isGreaterThanOrEqualTo(4000 - 8);
    }
}
