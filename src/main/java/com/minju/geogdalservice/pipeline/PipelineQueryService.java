package com.minju.geogdalservice.pipeline;

import com.minju.geogdalservice.common.exception.NotFoundException;
import com.minju.geogdalservice.dto.JobDto;
import com.minju.geogdalservice.dto.StatusHistoryDto;
import com.minju.geogdalservice.dto.ValidationResultDto;
import com.minju.geogdalservice.entity.DatasetVersion;
import com.minju.geogdalservice.repository.ProcessingJobRepository;
import com.minju.geogdalservice.repository.ValidationResultRepository;
import com.minju.geogdalservice.repository.VersionStatusHistoryRepository;
import com.minju.geogdalservice.service.DatasetService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PipelineQueryService {

    private final DatasetService datasetService;
    private final ProcessingJobRepository jobRepository;
    private final ValidationResultRepository validationResultRepository;
    private final VersionStatusHistoryRepository historyRepository;

    public JobDto getJob(long jobId) {
        return jobRepository.findById(jobId)
                .map(JobDto::from)
                .orElseThrow(() -> new NotFoundException("작업을 찾을 수 없습니다: " + jobId));
    }

    public List<JobDto> getJobs(String datasetName, int versionNo) {
        DatasetVersion version = datasetService.getVersion(datasetName, versionNo);
        return jobRepository.findByVersionIdOrderByIdDesc(version.getId()).stream().map(JobDto::from).toList();
    }

    public List<ValidationResultDto> getValidationResults(String datasetName, int versionNo) {
        DatasetVersion version = datasetService.getVersion(datasetName, versionNo);
        return validationResultRepository.findByVersionIdOrderByIdAsc(version.getId()).stream()
                .map(ValidationResultDto::from)
                .toList();
    }

    public List<StatusHistoryDto> getHistory(String datasetName, int versionNo) {
        DatasetVersion version = datasetService.getVersion(datasetName, versionNo);
        return historyRepository.findByVersionIdOrderByIdAsc(version.getId()).stream()
                .map(StatusHistoryDto::from)
                .toList();
    }
}
