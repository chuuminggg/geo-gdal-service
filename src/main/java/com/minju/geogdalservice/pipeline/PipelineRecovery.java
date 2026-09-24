package com.minju.geogdalservice.pipeline;

import com.minju.geogdalservice.entity.JobStatus;
import com.minju.geogdalservice.entity.ProcessingJob;
import com.minju.geogdalservice.repository.ProcessingJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * 서버가 작업 도중 종료되면 작업은 RUNNING/QUEUED 로, 버전은 VALIDATING/PROCESSING 으로 남는다.
 * 기동 시 이런 작업을 FAILED 로 정리해서 재시도 API 로 이어서 처리할 수 있게 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PipelineRecovery {

    private final ProcessingJobRepository jobRepository;
    private final PipelineStateService stateService;

    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterruptedJobs() {
        List<ProcessingJob> interrupted = jobRepository.findByStatusIn(Set.of(JobStatus.QUEUED, JobStatus.RUNNING));
        for (ProcessingJob job : interrupted) {
            stateService.failJob(job.getId(), "서버 재시작으로 작업이 중단되었습니다. 재시도해주세요.");
        }
        if (!interrupted.isEmpty()) {
            log.warn("Marked {} interrupted pipeline job(s) as FAILED", interrupted.size());
        }
    }
}
