package com.minju.geogdalservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 파이프라인 전용 스레드 풀.
     * GDAL 변환은 CPU/디스크를 많이 쓰므로 웹 요청 스레드와 분리하고 동시 실행 수를 제한한다.
     */
    @Bean(name = "pipelineExecutor")
    public Executor pipelineExecutor(@Value("${pipeline.executor.pool-size:2}") int poolSize,
                                     @Value("${pipeline.executor.queue-capacity:100}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(poolSize);
        executor.setMaxPoolSize(poolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("pipeline-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
