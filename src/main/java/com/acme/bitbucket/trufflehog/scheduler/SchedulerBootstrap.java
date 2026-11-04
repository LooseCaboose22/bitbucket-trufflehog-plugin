package com.acme.bitbucket.trufflehog.scheduler;

import com.acme.bitbucket.trufflehog.service.TrufflehogConfigurationService;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.scheduler.SchedulerService;
import com.atlassian.scheduler.SchedulerServiceException;
import com.atlassian.scheduler.config.JobConfig;
import com.atlassian.scheduler.config.JobId;
import com.atlassian.scheduler.config.JobRunnerKey;
import com.atlassian.scheduler.config.RunMode;
import com.atlassian.scheduler.config.Schedule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import javax.inject.Named;

/**
 * Registers the {@link FullScanJobRunner} with the cluster-aware scheduler
 * service and re-schedules it from the persisted cron expression when settings
 * change.
 */
@Named
@Component
public class SchedulerBootstrap implements InitializingBean, DisposableBean {
    private static final Logger LOG = LoggerFactory.getLogger(SchedulerBootstrap.class);

    public static final JobRunnerKey RUNNER_KEY =
            JobRunnerKey.of("com.acme.bitbucket.trufflehog.scheduled-scan-runner");
    public static final JobId JOB_ID =
            JobId.of("com.acme.bitbucket.trufflehog.scheduled-scan-job");

    private final SchedulerService schedulerService;
    private final FullScanJobRunner jobRunner;
    private final TrufflehogConfigurationService configurationService;

    @Inject
    public SchedulerBootstrap(
            @ComponentImport SchedulerService schedulerService,
            FullScanJobRunner jobRunner,
            TrufflehogConfigurationService configurationService) {
        this.schedulerService = schedulerService;
        this.jobRunner = jobRunner;
        this.configurationService = configurationService;
    }

    @Override
    public void afterPropertiesSet() {
        schedulerService.registerJobRunner(RUNNER_KEY, jobRunner);
        reschedule();
    }

    @Override
    public void destroy() {
        try {
            schedulerService.unscheduleJob(JOB_ID);
        } catch (Exception ex) {
            LOG.debug("Failed to unschedule TruffleHog job", ex);
        }
        schedulerService.unregisterJobRunner(RUNNER_KEY);
    }

    /**
     * Idempotently (re-)schedule the job from the current cron expression.
     */
    public void reschedule() {
        String cron = configurationService.getSettings().getCronExpression();
        if (cron == null || cron.trim().isEmpty()) {
            cron = "0 0 2 * * ?";
        }
        try {
            schedulerService.unscheduleJob(JOB_ID);
        } catch (Exception ignored) {
            // unscheduling a non-existent job is fine
        }
        try {
            schedulerService.scheduleJob(JOB_ID, JobConfig.forJobRunnerKey(RUNNER_KEY)
                    .withSchedule(Schedule.forCronExpression(cron))
                    .withRunMode(RunMode.RUN_ONCE_PER_CLUSTER));
            LOG.info("TruffleHog full scan scheduled with cron '{}'", cron);
        } catch (SchedulerServiceException ex) {
            LOG.error("Failed to schedule TruffleHog full scan with cron '{}'", cron, ex);
        }
    }
}
