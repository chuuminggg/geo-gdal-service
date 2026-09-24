package com.minju.geogdalservice.repository;

import com.minju.geogdalservice.entity.JobStatus;
import com.minju.geogdalservice.entity.ProcessingJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProcessingJobRepository extends JpaRepository<ProcessingJob, Long> {

    List<ProcessingJob> findByVersionIdOrderByIdDesc(Long versionId);

    Optional<ProcessingJob> findFirstByVersionIdOrderByIdDesc(Long versionId);

    List<ProcessingJob> findByStatusIn(Collection<JobStatus> statuses);
}
