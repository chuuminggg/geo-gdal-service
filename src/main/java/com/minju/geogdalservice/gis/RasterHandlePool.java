package com.minju.geogdalservice.gis;

import com.minju.geogdalservice.entity.DatasetType;
import com.minju.geogdalservice.pipeline.PipelineEvents;
import com.minju.geogdalservice.util.GdalInitializer;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 열린 래스터 핸들(OpenRaster) 풀.
 *
 * /vsis3/ COG 를 열 때마다 S3 에 HEAD/헤더 Range 요청을 보내고 좌표 변환 객체를 만드는 비용이
 * 단건 조회 시간의 대부분을 차지하므로, 한 번 연 핸들을 반납받아 재사용한다.
 *
 * - GDAL Dataset 은 스레드 안전하지 않으므로 핸들은 한 번에 한 요청에만 빌려준다(borrow/반납).
 * - 키는 버전별로 다른 S3 경로이므로 새 버전이 배포되면 자연히 새 핸들을 쓰고,
 *   배포 이벤트 시 해당 데이터셋의 유휴 핸들은 닫는다.
 * - 경로당 유휴 핸들 수를 제한해 네이티브 메모리가 무한히 늘지 않게 한다.
 */
@Slf4j
@Component
public class RasterHandlePool {

    private final GdalInitializer gdalInitializer;
    private final int maxIdlePerPath;
    private final Map<String, BlockingDeque<OpenRaster>> idle = new ConcurrentHashMap<>();
    // 배포 교체로 더 이상 쓰지 않는 경로 (반납되는 핸들은 닫는다)
    private final Set<String> retired = ConcurrentHashMap.newKeySet();

    private final AtomicLong opened = new AtomicLong();
    private final AtomicLong reused = new AtomicLong();

    public RasterHandlePool(GdalInitializer gdalInitializer,
                            @Value("${geo.raster.pool.max-idle-per-raster:16}") int maxIdlePerPath) {
        this.gdalInitializer = gdalInitializer;
        this.maxIdlePerPath = maxIdlePerPath;
    }

    public OpenRaster borrow(String gdalPath) {
        gdalInitializer.requireAvailable();
        retired.remove(gdalPath);   // 롤백으로 다시 서비스되는 경로
        BlockingDeque<OpenRaster> queue = idle.get(gdalPath);
        OpenRaster handle = queue == null ? null : queue.pollFirst();
        if (handle == null) {
            handle = OpenRaster.open(gdalPath);
            opened.incrementAndGet();
        } else {
            reused.incrementAndGet();
        }
        handle.releaseTo(this::release);
        return handle;
    }

    private void release(OpenRaster handle) {
        if (retired.contains(handle.path())) {
            handle.destroy();
            return;
        }
        BlockingDeque<OpenRaster> queue = idle.computeIfAbsent(handle.path(),
                p -> new LinkedBlockingDeque<>(maxIdlePerPath));
        // 최근에 반납된 핸들을 먼저 빌려주면(LIFO) 블록 캐시가 따뜻한 핸들을 재사용하게 된다
        if (!queue.offerFirst(handle)) {
            handle.destroy();
        }
    }

    /**
     * 래스터 데이터셋이 새로 배포되면 이전 버전 경로의 유휴 핸들을 닫는다
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPublished(PipelineEvents.DatasetPublished event) {
        if (event.type() != DatasetType.RASTER) {
            return;
        }
        String prefix = "/processed/" + event.datasetName() + "/";
        idle.keySet().stream().filter(path -> path.contains(prefix)).forEach(this::retire);
    }

    private void retire(String path) {
        retired.add(path);
        BlockingDeque<OpenRaster> queue = idle.remove(path);
        if (queue != null) {
            queue.forEach(OpenRaster::destroy);
            log.info("Retired {} idle raster handle(s): {}", queue.size(), path);
        }
    }

    @PreDestroy
    void closeAll() {
        idle.values().forEach(q -> q.forEach(OpenRaster::destroy));
        idle.clear();
    }

    public long openedCount() {
        return opened.get();
    }

    public long reusedCount() {
        return reused.get();
    }
}
