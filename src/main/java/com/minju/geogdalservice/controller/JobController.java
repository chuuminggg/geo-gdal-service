package com.minju.geogdalservice.controller;

import com.minju.geogdalservice.common.dto.CommonResponse;
import com.minju.geogdalservice.dto.JobDto;
import com.minju.geogdalservice.pipeline.IngestService;
import com.minju.geogdalservice.pipeline.PipelineQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobController {

    private final PipelineQueryService queryService;
    private final IngestService ingestService;

    /**
     * 파이프라인 작업 상태 조회 (업로드 후 polling)
     */
    @GetMapping("/{jobId}")
    public CommonResponse<JobDto> getJob(@PathVariable long jobId) {
        return CommonResponse.success(queryService.getJob(jobId));
    }

    /**
     * 실패한 작업을 실패한 단계부터 재시도
     */
    @PostMapping("/{jobId}/retry")
    public CommonResponse<JobDto> retry(@PathVariable long jobId) {
        long newJobId = ingestService.retry(jobId).getId();
        return CommonResponse.success(202, "재시도 작업이 등록되었습니다.", queryService.getJob(newJobId));
    }
}
