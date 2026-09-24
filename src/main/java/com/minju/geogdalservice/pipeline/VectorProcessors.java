package com.minju.geogdalservice.pipeline;

import com.minju.geogdalservice.entity.DatasetType;
import com.minju.geogdalservice.gis.VectorData;
import com.minju.geogdalservice.gis.VectorFeature;
import com.minju.geogdalservice.gis.VectorReader;
import com.minju.geogdalservice.network.RoadNetwork;
import com.minju.geogdalservice.network.RoadNetworkBuilder;
import com.minju.geogdalservice.repository.PoiRepository;
import com.minju.geogdalservice.repository.PoiRepository.PoiRow;
import com.minju.geogdalservice.repository.RoadNetworkRepository;
import com.minju.geogdalservice.util.TempWorkspace;
import com.minju.geogdalservice.validation.ValidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * 벡터(도로 네트워크, POI): OGR 로 읽어 검수 -> PostGIS 에 버전별 스냅샷으로 적재
 */
public final class VectorProcessors {

    private static final Set<String> VECTOR_EXTENSIONS = Set.of(".geojson", ".json", ".zip", ".gpkg");

    private VectorProcessors() {
    }

    @RequiredArgsConstructor
    abstract static class VectorProcessor implements DatasetProcessor {

        protected final VectorReader vectorReader;
        protected final ValidationService validationService;
        protected final TransactionTemplate transactionTemplate;

        @Override
        public Set<String> allowedExtensions() {
            return VECTOR_EXTENSIONS;
        }

        @Override
        public ValidationOutcome validate(Path rawFile) {
            VectorData data;
            try {
                data = vectorReader.read(rawFile);
            } catch (IllegalArgumentException e) {
                return ValidationOutcome.unreadable(e.getMessage());
            }
            return new ValidationOutcome(validationService.validateVector(data, type()),
                    data.footprint(), data.features().size(), null);
        }
    }

    @Slf4j
    @Component
    public static class RoadNetworkProcessor extends VectorProcessor {

        private final RoadNetworkRepository roadNetworkRepository;

        public RoadNetworkProcessor(VectorReader vectorReader, ValidationService validationService,
                                    TransactionTemplate transactionTemplate,
                                    RoadNetworkRepository roadNetworkRepository) {
            super(vectorReader, validationService, transactionTemplate);
            this.roadNetworkRepository = roadNetworkRepository;
        }

        @Override
        public DatasetType type() {
            return DatasetType.ROAD_NETWORK;
        }

        @Override
        public String process(JobContext context, Path rawFile, TempWorkspace workspace) {
            RoadNetwork network = RoadNetworkBuilder.build(vectorReader.read(rawFile).features());

            // 재시도 시에도 같은 결과가 되도록 기존 적재분을 지우고 한 트랜잭션으로 적재 (멱등)
            transactionTemplate.executeWithoutResult(status -> {
                roadNetworkRepository.deleteByVersion(context.versionId());
                roadNetworkRepository.insert(context.versionId(), network);
            });
            log.info("Road network loaded: dataset={}, v{}, nodes={}, links={}, skipped={}",
                    context.datasetName(), context.versionNo(),
                    network.nodes().size(), network.links().size(), network.skippedFeatures());
            return null;
        }
    }

    @Slf4j
    @Component
    public static class PoiProcessor extends VectorProcessor {

        private final PoiRepository poiRepository;

        public PoiProcessor(VectorReader vectorReader, ValidationService validationService,
                            TransactionTemplate transactionTemplate, PoiRepository poiRepository) {
            super(vectorReader, validationService, transactionTemplate);
            this.poiRepository = poiRepository;
        }

        @Override
        public DatasetType type() {
            return DatasetType.POI;
        }

        @Override
        public String process(JobContext context, Path rawFile, TempWorkspace workspace) {
            List<PoiRow> pois = vectorReader.read(rawFile).features().stream()
                    .filter(f -> f.geometry() instanceof Point p && !p.isEmpty() && p.isValid())
                    .map(PoiProcessor::toRow)
                    .toList();

            transactionTemplate.executeWithoutResult(status -> {
                poiRepository.deleteByVersion(context.versionId());
                poiRepository.insert(context.versionId(), pois);
            });
            log.info("POI loaded: dataset={}, v{}, count={}", context.datasetName(), context.versionNo(), pois.size());
            return null;
        }

        private static PoiRow toRow(VectorFeature f) {
            Point p = (Point) f.geometry();
            return new PoiRow(
                    f.attr("id", "poi_id"),
                    f.attr("name", "poi_name", "title"),
                    f.attr("category", "amenity", "shop", "type"),
                    f.attr("address", "addr", "addr:full", "road_address"),
                    p.getX(), p.getY());
        }
    }
}
