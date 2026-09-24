package com.minju.geogdalservice.service;

import com.minju.geogdalservice.common.exception.NotFoundException;
import com.minju.geogdalservice.entity.Dataset;
import com.minju.geogdalservice.entity.DatasetType;
import com.minju.geogdalservice.entity.DatasetVersion;
import com.minju.geogdalservice.pipeline.PipelineEvents;
import com.minju.geogdalservice.repository.DatasetRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ActiveVersionResolverTest {

    private final DatasetRepository repository = mock(DatasetRepository.class);

    @Test
    void TTL_동안은_DB를_다시_조회하지_않는다() {
        when(repository.findWithActiveVersionByName("roads")).thenReturn(Optional.of(dataset(10L, 1)));
        ActiveVersionResolver resolver = new ActiveVersionResolver(repository, Duration.ofMinutes(1));

        for (int i = 0; i < 100; i++) {
            assertThat(resolver.resolve("roads").versionNo()).isEqualTo(1);
        }
        verify(repository, times(1)).findWithActiveVersionByName("roads");
    }

    @Test
    void 배포_이벤트를_받으면_즉시_새_버전을_읽는다() {
        when(repository.findWithActiveVersionByName("roads"))
                .thenReturn(Optional.of(dataset(10L, 1)), Optional.of(dataset(11L, 2)));
        ActiveVersionResolver resolver = new ActiveVersionResolver(repository, Duration.ofMinutes(1));

        assertThat(resolver.resolve("roads").versionId()).isEqualTo(10L);
        resolver.onPublished(new PipelineEvents.DatasetPublished(1L, "roads", DatasetType.ROAD_NETWORK, 11L));
        assertThat(resolver.resolve("roads").versionId()).isEqualTo(11L);
    }

    @Test
    void TTL이_지나면_다시_조회한다_다른_인스턴스의_배포_반영() {
        when(repository.findWithActiveVersionByName("roads"))
                .thenReturn(Optional.of(dataset(10L, 1)), Optional.of(dataset(11L, 2)));
        ActiveVersionResolver resolver = new ActiveVersionResolver(repository, Duration.ZERO);

        assertThat(resolver.resolve("roads").versionNo()).isEqualTo(1);
        assertThat(resolver.resolve("roads").versionNo()).isEqualTo(2);
    }

    @Test
    void 배포되지_않은_데이터셋과_없는_데이터셋() {
        when(repository.findWithActiveVersionByName("draft")).thenReturn(Optional.of(dataset(null, null)));
        when(repository.findWithActiveVersionByName(anyString())).thenAnswer(inv ->
                "draft".equals(inv.getArgument(0)) ? Optional.of(dataset(null, null)) : Optional.empty());
        ActiveVersionResolver resolver = new ActiveVersionResolver(repository, Duration.ofMinutes(1));

        assertThat(resolver.resolve("draft").published()).isFalse();
        assertThatThrownBy(() -> resolver.resolve("none")).isInstanceOf(NotFoundException.class);
    }

    private Dataset dataset(Long versionId, Integer versionNo) {
        Dataset dataset = Dataset.builder().name("roads").type(DatasetType.ROAD_NETWORK).build();
        if (versionId != null) {
            DatasetVersion version = DatasetVersion.builder().dataset(dataset).versionNo(versionNo)
                    .originalFileName("a").rawKey("k").checksum("c").fileSize(1).build();
            ReflectionTestUtils.setField(version, "id", versionId);
            dataset.changeActiveVersion(version);
        }
        return dataset;
    }
}
