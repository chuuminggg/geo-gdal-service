package com.minju.geogdalservice.routing;

import com.minju.geogdalservice.common.exception.NotFoundException;
import com.minju.geogdalservice.entity.Dataset;
import com.minju.geogdalservice.entity.DatasetType;
import com.minju.geogdalservice.entity.DatasetVersion;
import com.minju.geogdalservice.pipeline.PipelineEvents;
import com.minju.geogdalservice.repository.DatasetRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 도로 네트워크 버전별 그래프 캐시.
 *
 * - 캐시 키가 버전 ID 이므로, 요청 시점의 배포(active) 버전과 항상 일치하는 그래프를 사용한다.
 *   (다른 인스턴스에서 배포가 일어나도 DB 의 active 버전을 보고 새 그래프를 로드)
 * - 같은 버전을 동시에 요청해도 computeIfAbsent 로 한 번만 로드한다.
 * - 배포 커밋 직후 이전 버전 그래프를 즉시(동기) 버리고, 새 버전 그래프는 백그라운드에서 미리 로드한다.
 * - DEM 이 재배포되면 고도 세대(generation)를 올린다. 로드 시작 시점의 세대를 그래프와 함께 저장해 두므로,
 *   DEM 교체와 동시에 진행 중이던 로드가 이전 고도로 만든 그래프를 캐시에 넣더라도 다음 요청에서 버려진다.
 */
@Slf4j
@Component
public class RoadGraphProvider {

    private final DatasetRepository datasetRepository;
    private final RoadGraphLoader loader;
    private final Executor warmUpExecutor;
    private final Map<Long, CachedGraph> cache = new ConcurrentHashMap<>();
    private final AtomicLong elevationGeneration = new AtomicLong();

    private record CachedGraph(RoadGraph graph, long generation) {
    }

    public RoadGraphProvider(DatasetRepository datasetRepository, RoadGraphLoader loader,
                             @Qualifier("pipelineExecutor") Executor warmUpExecutor) {
        this.datasetRepository = datasetRepository;
        this.loader = loader;
        this.warmUpExecutor = warmUpExecutor;
    }

    public RoadGraph get(String datasetName) {
        Dataset dataset = datasetRepository.findWithActiveVersionByName(datasetName)
                .orElseThrow(() -> new NotFoundException("데이터셋을 찾을 수 없습니다: " + datasetName));
        if (dataset.getType() != DatasetType.ROAD_NETWORK) {
            throw new IllegalArgumentException("도로 네트워크 데이터셋이 아닙니다: " + datasetName);
        }
        DatasetVersion active = dataset.getActiveVersion();
        if (active == null) {
            throw new NotFoundException("배포된 도로 네트워크 버전이 없습니다: " + datasetName);
        }
        long generation = elevationGeneration.get();
        CachedGraph cached = cache.compute(active.getId(), (id, current) ->
                current != null && current.generation() == generation
                        ? current
                        : new CachedGraph(loader.load(id, datasetName, active.getVersionNo()), generation));
        evictOtherVersions(datasetName, active.getId());
        return cached.graph();
    }

    // 무효화는 커밋 직후 동기로 처리해서, 배포 API 응답 이후의 요청이 이전 그래프를 쓰지 않게 한다
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPublished(PipelineEvents.DatasetPublished event) {
        if (event.type() == DatasetType.ROAD_NETWORK) {
            evictOtherVersions(event.datasetName(), event.versionId());
            warmUpExecutor.execute(() -> warmUp(event.datasetName()));
        } else if (event.type() == DatasetType.RASTER && event.datasetName().equals(loader.elevationDataset())) {
            // DEM 이 바뀌면 모든 그래프의 노드 고도가 바뀌어야 하므로 전부 다시 로드
            log.info("Elevation dataset {} republished, clearing road graph cache", event.datasetName());
            elevationGeneration.incrementAndGet();
            cache.clear();
        }
    }

    private void warmUp(String datasetName) {
        try {
            get(datasetName);
        } catch (RuntimeException e) {
            log.warn("Road graph warm-up failed: dataset={}, {}", datasetName, e.getMessage());
        }
    }

    private void evictOtherVersions(String datasetName, long activeVersionId) {
        cache.entrySet().removeIf(entry ->
                entry.getValue().graph().datasetName().equals(datasetName) && entry.getKey() != activeVersionId);
    }

    public int cachedGraphCount() {
        return cache.size();
    }
}
