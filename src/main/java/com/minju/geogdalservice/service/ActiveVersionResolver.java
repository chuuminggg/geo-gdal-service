package com.minju.geogdalservice.service;

import com.minju.geogdalservice.common.exception.NotFoundException;
import com.minju.geogdalservice.entity.Dataset;
import com.minju.geogdalservice.entity.DatasetType;
import com.minju.geogdalservice.pipeline.PipelineEvents;
import com.minju.geogdalservice.repository.DatasetRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 데이터셋 이름 -> 서비스(active) 버전 캐시.
 *
 * 조회 API(경로탐색, 고도, 픽셀)가 요청마다 DB 에서 active 버전을 조회하지 않도록 한다.
 * - 이 인스턴스에서 배포하면 커밋 직후 즉시 무효화되어 다음 요청부터 새 버전을 사용한다.
 * - 다른 인스턴스에서 배포한 경우를 위해 TTL 이 지나면 DB 에서 다시 읽는다.
 * - DB 가 잠시 불안정해도 캐시된 동안은 조회 API 가 계속 동작한다.
 */
@Component
public class ActiveVersionResolver {

    public record ActiveVersion(String datasetName, DatasetType type, Long versionId, Integer versionNo,
                                String processedKey) {
        public boolean published() {
            return versionId != null;
        }
    }

    private record Entry(ActiveVersion value, long expiresAtNanos) {
    }

    private final DatasetRepository datasetRepository;
    private final long ttlNanos;
    private final Map<String, Entry> cache = new ConcurrentHashMap<>();

    public ActiveVersionResolver(DatasetRepository datasetRepository,
                                 @Value("${geo.active-version-cache.ttl:5s}") Duration ttl) {
        this.datasetRepository = datasetRepository;
        this.ttlNanos = ttl.toNanos();
    }

    public ActiveVersion resolve(String datasetName) {
        Entry entry = cache.get(datasetName);
        if (entry != null && System.nanoTime() < entry.expiresAtNanos()) {
            return entry.value();
        }
        Dataset dataset = datasetRepository.findWithActiveVersionByName(datasetName)
                .orElseThrow(() -> new NotFoundException("데이터셋을 찾을 수 없습니다: " + datasetName));
        var active = dataset.getActiveVersion();
        ActiveVersion value = new ActiveVersion(dataset.getName(), dataset.getType(),
                active == null ? null : active.getId(),
                active == null ? null : active.getVersionNo(),
                active == null ? null : active.getProcessedKey());
        cache.put(datasetName, new Entry(value, System.nanoTime() + ttlNanos));
        return value;
    }

    public void evict(String datasetName) {
        cache.remove(datasetName);
    }

    // 다른 배포 리스너(그래프 캐시 등)보다 먼저 무효화
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPublished(PipelineEvents.DatasetPublished event) {
        evict(event.datasetName());
    }
}
