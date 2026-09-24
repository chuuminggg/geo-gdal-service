package com.minju.geogdalservice.gis;

import com.minju.geogdalservice.support.GdalTestSupport;
import com.minju.geogdalservice.util.COGUtils;
import org.gdal.gdal.Dataset;
import org.gdal.gdal.gdal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;
import org.locationtech.jts.geom.Envelope;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

@EnabledIf("com.minju.geogdalservice.support.GdalTestSupport#gdalAvailable")
class RasterInspectorGdalTest {

    @TempDir
    Path tempDir;

    private final RasterInspector inspector = new RasterInspector(GdalTestSupport.initializer());

    @Test
    void EPSG5186_DEM에서_좌표계_범위_해상도_통계를_추출한다() {
        // EPSG:5186 (중부원점) x=200000, y=550000 부근 = 서울 (경도 127, 위도 약 37.55)
        // 100x100, 30m 해상도, 좌상단 10x10 블록은 NoData(-9999)
        Path tif = GdalTestSupport.createGeoTiff(tempDir.resolve("dem.tif"), 5186, 200_000, 550_000, 30,
                100, 100, (col, row) -> (col < 10 && row < 10) ? -9999 : 10 + col + row, -9999.0);

        RasterInfo info = inspector.inspect(tif);

        assertThat(info.width()).isEqualTo(100);
        assertThat(info.bandCount()).isEqualTo(1);
        assertThat(info.dataType()).isEqualTo("Float32");
        assertThat(info.epsg()).isEqualTo(5186);
        assertThat(info.resolutionM()).isCloseTo(30.0, within(1e-6));
        assertThat(info.noDataRatio()).isCloseTo(0.01, within(1e-9));
        assertThat(info.minValue()).isEqualTo(10.0 + 10);  // NoData 블록 밖의 최소값 (col=10,row=0)
        assertThat(info.maxValue()).isEqualTo(10.0 + 99 + 99);

        Envelope env = info.footprint().getEnvelopeInternal();
        assertThat(info.footprint().getSRID()).isEqualTo(4326);
        assertThat(env.getMinX()).isCloseTo(127.0, within(0.01));
        assertThat(env.getMaxY()).isCloseTo(37.55, within(0.01));
        // 3km x 3km 영역
        assertThat(GeometryUtils.haversine(env.getMinX(), env.getMinY(), env.getMaxX(), env.getMinY()))
                .isCloseTo(3000, within(50.0));
    }

    @Test
    void 경위도_좌표계_래스터는_위도를_보정한_미터_해상도를_계산한다() {
        Path tif = GdalTestSupport.createGeoTiff(tempDir.resolve("wgs.tif"), 4326, 127.0, 37.6, 0.0001,
                50, 50, (c, r) -> 1, null);

        RasterInfo info = inspector.inspect(tif);

        assertThat(info.epsg()).isEqualTo(4326);
        // 0.0001도 * 111320m * cos(37.6도) ≈ 8.8m
        assertThat(info.resolutionM()).isCloseTo(8.8, within(0.1));
        assertThat(info.footprint().getEnvelopeInternal().getMinX()).isCloseTo(127.0, within(1e-9));
    }

    @Test
    void 좌표계가_없는_래스터는_footprint가_없다() {
        Path tif = GdalTestSupport.createGeoTiff(tempDir.resolve("nocrs.tif"), null, 0, 0, 1,
                10, 10, (c, r) -> 1, null);

        RasterInfo info = inspector.inspect(tif);

        assertThat(info.hasCrs()).isFalse();
        assertThat(info.footprint()).isNull();
        assertThat(info.epsg()).isNull();
    }

    @Test
    void 래스터가_아닌_파일은_예외() throws Exception {
        Path txt = java.nio.file.Files.writeString(tempDir.resolve("a.tif"), "not a tiff");
        assertThatThrownBy(() -> inspector.inspect(txt)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void COG로_변환하면_COG_레이아웃과_오버뷰를_가진다() {
        Path tif = GdalTestSupport.createGeoTiff(tempDir.resolve("big.tif"), 5186, 200_000, 550_000, 10,
                2048, 2048, (c, r) -> c % 255, null);
        Path cog = tempDir.resolve("big_cog.tif");

        new COGUtils(GdalTestSupport.initializer()).transformToCOG(tif, cog);

        Dataset ds = gdal.Open(cog.toString());
        try {
            assertThat(ds.GetMetadataItem("LAYOUT", "IMAGE_STRUCTURE")).isEqualTo("COG");
            assertThat(ds.GetRasterBand(1).GetOverviewCount()).isGreaterThan(0);
        } finally {
            ds.delete();
        }
    }
}
