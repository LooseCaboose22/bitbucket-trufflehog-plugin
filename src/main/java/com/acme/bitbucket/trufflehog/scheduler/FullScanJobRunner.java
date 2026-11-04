package com.acme.bitbucket.trufflehog.scheduler;

import com.acme.bitbucket.trufflehog.model.ScanTrigger;
import com.acme.bitbucket.trufflehog.service.ScanOrchestrator;
import com.atlassian.scheduler.JobRunner;
import com.atlassian.scheduler.JobRunnerRequest;
import com.atlassian.scheduler.JobRunnerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import javax.inject.Named;

/**
 * Runs the full-cluster scheduled scan. Registered with the scheduler service
 * by {@link SchedulerBootstrap}.
 */
@Named
@Component
public class FullScanJobRunner implements JobRunner {
    private static final Logger LOG = LoggerFactory.getLogger(FullScanJobRunner.class);

    private final ScanOrchestrator scanOrchestrator;

    @Inject
    public FullScanJobRunner(ScanOrchestrator scanOrchestrator) {
        this.scanOrchestrator = scanOrchestrator;
    }

    @Override
    public JobRunnerResponse runJob(JobRunnerRequest request) {
        long start = System.currentTimeMillis();
        LOG.info("Starting TruffleHog scheduled full scan");
        try {
            scanOrchestrator.scanAllRepositories(ScanTrigger.SCHEDULED);
            long elapsed = System.currentTimeMillis() - start;
            LOG.info("TruffleHog scheduled full scan finished in {} ms", elapsed);
            return JobRunnerResponse.success("scan complete in " + elapsed + " ms");
        } catch (RuntimeException ex) {
            LOG.error("TruffleHog scheduled scan failed", ex);
            return JobRunnerResponse.failed(ex);
        }
    }
}
