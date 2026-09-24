package com.minju.geogdalservice.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class LocalstackTestcontainersConfig {

    /**
     * GDAL 설정(/vsis3/ 엔드포인트)은 프로세스 전역이므로, 테스트 컨텍스트가 여러 개 떠도
     * 모두 같은 LocalStack 을 바라보도록 JVM 당 하나의 컨테이너를 공유한다.
     */
    private static final LocalStackContainer LOCALSTACK =
            new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.8"))
                    .withServices(LocalStackContainer.Service.S3);

    @Bean
    LocalStackContainer localStackContainer() {
        if (!LOCALSTACK.isRunning()) {
            LOCALSTACK.start();
        }
        return LOCALSTACK;
    }

    @Bean
    DynamicPropertyRegistrar s3Properties(LocalStackContainer localStack) {
        return registry -> {
            registry.add("aws.s3.endpoint", () -> localStack.getEndpoint().toString());
            registry.add("aws.s3.region", localStack::getRegion);
            registry.add("aws.access.key", localStack::getAccessKey);
            registry.add("aws.secret.key", localStack::getSecretKey);
        };
    }
}
