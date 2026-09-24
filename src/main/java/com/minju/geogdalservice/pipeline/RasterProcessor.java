package com.minju.geogdalservice.pipeline;

import com.minju.geogdalservice.entity.DatasetType;
import com.minju.geogdalservice.gis.RasterInfo;
import com.minju.geogdalservice.gis.RasterInspector;
import com.minju.geogdalservice.service.S3Service;
import com.minju.geogdalservice.util.COGUtils;
import com.minju.geogdalservice.util.TempWorkspace;
import com.minju.geogdalservice.validation.ValidationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Set;

/**
 * 래스터: GDAL 메타데이터 검수 -> COG 변환 후 S3 processed/ 에 저장
 */
@Component
@RequiredArgsConstructor
public class RasterProcessor implements DatasetProcessor {

    private final RasterInspector rasterInspector;
    private final ValidationService validationService;
    private final COGUtils cogUtils;
    private final S3Service s3Service;

    @Value("${aws.s3.bucket.name}")
    private String bucketName;

    @Override
    public DatasetType type() {
        return DatasetType.RASTER;
    }

    @Override
    public Set<String> allowedExtensions() {
        return Set.of(".tif", ".tiff");
    }

    @Override
    public ValidationOutcome validate(Path rawFile) {
        RasterInfo info;
        try {
            info = rasterInspector.inspect(rawFile);
        } catch (IllegalArgumentException e) {
            return ValidationOutcome.unreadable(e.getMessage());
        }
        return new ValidationOutcome(validationService.validateRaster(info), info.footprint(), null, info);
    }

    @Override
    public String process(JobContext context, Path rawFile, TempWorkspace workspace) {
        Path cog = workspace.resolve("cog.tif");
        cogUtils.transformToCOG(rawFile, cog);

        String processedKey = "processed/%s/v%d/%s_cog.tif".formatted(
                context.datasetName(), context.versionNo(), context.datasetName());
        s3Service.upload(bucketName, processedKey, cog, "image/tiff");
        return processedKey;
    }
}
