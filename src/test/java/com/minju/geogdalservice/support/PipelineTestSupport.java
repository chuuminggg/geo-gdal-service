package com.minju.geogdalservice.support;

import com.jayway.jsonpath.JsonPath;
import com.minju.geogdalservice.dto.JobDto;
import com.minju.geogdalservice.entity.JobStatus;
import com.minju.geogdalservice.pipeline.PipelineQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;

import java.time.Duration;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 파이프라인 E2E 테스트 공통 기반: PostGIS + LocalStack(S3) + GDAL
 * GDAL 네이티브 라이브러리와 Docker 가 모두 있는 환경(docker/gdal 이미지 + docker.sock)에서 실행된다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgisTestcontainersConfig.class, LocalstackTestcontainersConfig.class})
@Testcontainers(disabledWithoutDocker = true)
public abstract class PipelineTestSupport {

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected PipelineQueryService queryService;
    @Autowired
    protected S3Client s3Client;

    @Value("${aws.s3.bucket.name}")
    protected String bucketName;

    @BeforeEach
    void createBucket() {
        try {
            s3Client.createBucket(b -> b.bucket(bucketName));
        } catch (BucketAlreadyOwnedByYouException ignored) {
            // 이전 테스트에서 생성됨
        }
    }

    protected String uniqueName(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    protected void createDataset(String name, String type) throws Exception {
        mockMvc.perform(post("/api/datasets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s\",\"type\":\"%s\"}".formatted(name, type)))
                .andExpect(status().isOk());
    }

    protected long upload(String dataset, String fileName, byte[] content, boolean autoPublish) throws Exception {
        String body = mockMvc.perform(multipart("/api/datasets/{name}/versions", dataset)
                        .file(new MockMultipartFile("file", fileName, MediaType.APPLICATION_OCTET_STREAM_VALUE, content))
                        .param("autoPublish", String.valueOf(autoPublish)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.jobId")).longValue();
    }

    // 파이프라인 작업이 끝날 때까지 대기
    protected JobDto awaitJob(long jobId) {
        await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofMillis(200))
                .until(() -> {
                    JobStatus s = queryService.getJob(jobId).getStatus();
                    return s == JobStatus.SUCCEEDED || s == JobStatus.FAILED;
                });
        return queryService.getJob(jobId);
    }

    protected JobDto uploadAndWait(String dataset, String fileName, byte[] content, boolean autoPublish) throws Exception {
        return awaitJob(upload(dataset, fileName, content, autoPublish));
    }
}
