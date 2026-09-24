package com.minju.geogdalservice.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class LocalstackTestcontainersConfig {

    @Bean
    LocalStackContainer localStackContainer() {
        return new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.8"))
                .withServices(LocalStackContainer.Service.S3);
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
