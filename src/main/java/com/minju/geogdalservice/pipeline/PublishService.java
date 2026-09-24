package com.minju.geogdalservice.pipeline;

import com.minju.geogdalservice.common.exception.NotFoundException;
import com.minju.geogdalservice.entity.Dataset;
import com.minju.geogdalservice.entity.DatasetVersion;
import com.minju.geogdalservice.entity.VersionStatus;
import com.minju.geogdalservice.repository.DatasetRepository;
import com.minju.geogdalservice.repository.DatasetVersionRepository;
import com.minju.geogdalservice.service.DatasetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;

/**
 * 배포: 데이터셋의 서비스(active) 버전 전환과 롤백
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PublishService {

    private final DatasetService datasetService;
    private final DatasetRepository datasetRepository;
    private final DatasetVersionRepository versionRepository;
    private final PipelineStateService stateService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * PROCESSED(신규) 또는 ARCHIVED(롤백) 버전을 서비스 버전으로 전환한다.
     * 기존 서비스 버전은 ARCHIVED 가 되며, 모든 변경은 한 트랜잭션에서 원자적으로 처리된다.
     */
    public DatasetVersion publish(String datasetName, int versionNo) {
        Dataset dataset = lockDataset(datasetName);
        DatasetVersion target = versionRepository.findByDatasetIdAndVersionNo(dataset.getId(), versionNo)
                .orElseThrow(() -> new NotFoundException("버전을 찾을 수 없습니다: %s v%d".formatted(datasetName, versionNo)));

        DatasetVersion current = dataset.getActiveVersion();
        if (current != null && current.getId().equals(target.getId())) {
            return target;   // 이미 서비스 중 (멱등)
        }
        if (!target.getStatus().canTransitionTo(VersionStatus.PUBLISHED)) {
            throw new IllegalStateException("%s 상태의 버전은 배포할 수 없습니다. (PROCESSED 또는 ARCHIVED 만 가능)"
                    .formatted(target.getStatus()));
        }

        if (current != null) {
            stateService.transition(current, VersionStatus.ARCHIVED, "v%d 배포로 교체".formatted(versionNo));
        }
        stateService.transition(target, VersionStatus.PUBLISHED,
                current == null ? "최초 배포" : "v%d -> v%d 배포".formatted(current.getVersionNo(), versionNo));
        dataset.changeActiveVersion(target);

        eventPublisher.publishEvent(new PipelineEvents.DatasetPublished(
                dataset.getId(), dataset.getName(), dataset.getType(), target.getId()));
        log.info("Dataset published: {} v{}", datasetName, versionNo);
        return target;
    }

    /**
     * 직전에 서비스되던 버전으로 롤백
     */
    public DatasetVersion rollback(String datasetName) {
        Dataset dataset = datasetService.getDataset(datasetName);
        DatasetVersion previous = versionRepository.findByDatasetIdOrderByVersionNoDesc(dataset.getId()).stream()
                .filter(v -> v.getStatus() == VersionStatus.ARCHIVED)
                .max(Comparator.comparing(DatasetVersion::getPublishedAt))
                .orElseThrow(() -> new IllegalStateException("롤백할 이전 배포 버전이 없습니다."));
        return publish(datasetName, previous.getVersionNo());
    }

    private Dataset lockDataset(String datasetName) {
        Dataset dataset = datasetService.getDataset(datasetName);
        return datasetRepository.findByIdForUpdate(dataset.getId()).orElseThrow();
    }
}
