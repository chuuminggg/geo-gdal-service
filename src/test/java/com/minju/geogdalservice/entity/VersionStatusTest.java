package com.minju.geogdalservice.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VersionStatusTest {

    @Test
    void 정상_파이프라인_순서로_전이할_수_있다() {
        DatasetVersion version = newVersion();
        version.changeStatus(VersionStatus.VALIDATING);
        version.changeStatus(VersionStatus.VALIDATED);
        version.changeStatus(VersionStatus.PROCESSING);
        version.changeStatus(VersionStatus.PROCESSED);
        version.changeStatus(VersionStatus.PUBLISHED);

        assertThat(version.getStatus()).isEqualTo(VersionStatus.PUBLISHED);
        assertThat(version.getPublishedAt()).isNotNull();
    }

    @Test
    void 검수를_건너뛰고_배포할_수_없다() {
        DatasetVersion version = newVersion();
        assertThatThrownBy(() -> version.changeStatus(VersionStatus.PUBLISHED))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 불합격_버전은_어떤_상태로도_전이할_수_없다() {
        for (VersionStatus target : VersionStatus.values()) {
            assertThat(VersionStatus.REJECTED.canTransitionTo(target)).isFalse();
        }
    }

    @Test
    void 보관된_버전은_다시_배포_가능하다_롤백() {
        assertThat(VersionStatus.PUBLISHED.canTransitionTo(VersionStatus.ARCHIVED)).isTrue();
        assertThat(VersionStatus.ARCHIVED.canTransitionTo(VersionStatus.PUBLISHED)).isTrue();
    }

    @Test
    void 실패한_버전은_검수_또는_가공_단계부터_재시도할_수_있다() {
        assertThat(VersionStatus.FAILED.canTransitionTo(VersionStatus.VALIDATING)).isTrue();
        assertThat(VersionStatus.FAILED.canTransitionTo(VersionStatus.PROCESSING)).isTrue();
        assertThat(VersionStatus.FAILED.canTransitionTo(VersionStatus.PUBLISHED)).isFalse();
    }

    private DatasetVersion newVersion() {
        return DatasetVersion.builder().versionNo(1).originalFileName("a.tif").rawKey("raw/a")
                .checksum("0".repeat(64)).fileSize(1).build();
    }
}
