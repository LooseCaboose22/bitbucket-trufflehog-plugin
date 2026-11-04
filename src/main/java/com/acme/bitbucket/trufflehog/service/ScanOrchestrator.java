package com.acme.bitbucket.trufflehog.service;

import com.acme.bitbucket.trufflehog.ao.AoScanRun;
import com.acme.bitbucket.trufflehog.model.ScanStatus;
import com.acme.bitbucket.trufflehog.model.ScanTrigger;
import com.acme.bitbucket.trufflehog.model.TruffleHogRawFinding;
import com.acme.bitbucket.trufflehog.model.TrufflehogSettings;
import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.activeobjects.tx.Transactional;
import com.atlassian.bitbucket.auth.AuthenticationContext;
import com.atlassian.bitbucket.permission.Permission;
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.bitbucket.repository.RepositoryService;
import com.atlassian.bitbucket.user.EscalatedSecurityContext;
import com.atlassian.bitbucket.user.SecurityService;
import com.atlassian.bitbucket.util.Page;
import com.atlassian.bitbucket.util.PageRequest;
import com.atlassian.bitbucket.util.PageRequestImpl;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import net.java.ao.DBParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Orchestrates a scan: builds the {@link AoScanRun} row, invokes the scanner,
 * upserts findings, and finalises the run record.
 */
@Named
@Component
public class ScanOrchestrator {
    private static final Logger LOG = LoggerFactory.getLogger(ScanOrchestrator.class);

    private final ActiveObjects ao;
    private final TrufflehogScannerService scannerService;
    private final TrufflehogConfigurationService configurationService;
    private final FindingService findingService;
    private final NotificationService notificationService;
    private final RepositoryService repositoryService;
    private final SecurityService securityService;
    private final AuthenticationContext authenticationContext;

    @Inject
    public ScanOrchestrator(
            @ComponentImport ActiveObjects ao,
            TrufflehogScannerService scannerService,
            TrufflehogConfigurationService configurationService,
            FindingService findingService,
            NotificationService notificationService,
            @ComponentImport RepositoryService repositoryService,
            @ComponentImport SecurityService securityService,
            @ComponentImport AuthenticationContext authenticationContext) {
        this.ao = ao;
        this.scannerService = scannerService;
        this.configurationService = configurationService;
        this.findingService = findingService;
        this.notificationService = notificationService;
        this.repositoryService = repositoryService;
        this.securityService = securityService;
        this.authenticationContext = authenticationContext;
    }

    @Transactional
    public AoScanRun scanRepository(Repository repository, ScanTrigger trigger, String refName,
                                    String fromCommit, String toCommit) {
        TrufflehogSettings settings = configurationService.getSettings();
        long startedAt = System.currentTimeMillis();

        AoScanRun run = ao.create(AoScanRun.class,
                new DBParam("TRIGGER_TYPE", trigger.name()),
                new DBParam("STATUS", ScanStatus.RUNNING.name()));
        run.setRepositoryId((int) repository.getId());
        run.setProjectKey(repository.getProject().getKey());
        run.setRepositorySlug(repository.getSlug());
        run.setTriggeredBy(currentUsername());
        run.setStartedAt(startedAt);
        run.setRefName(refName == null ? "" : refName);
        run.setFromCommit(fromCommit == null ? "" : fromCommit);
        run.setToCommit(toCommit == null ? "" : toCommit);
        run.save();

        AtomicInteger total = new AtomicInteger();
        AtomicInteger verified = new AtomicInteger();
        AtomicInteger newCount = new AtomicInteger();
        List<TruffleHogRawFinding> newFindings = new ArrayList<>();

        try {
            List<TruffleHogRawFinding> findings = scannerService.scanRepository(
                    repository, settings, fromCommit, toCommit);
            for (TruffleHogRawFinding raw : findings) {
                boolean created = findingService.upsert(repository, raw);
                total.incrementAndGet();
                if (raw.isVerified()) {
                    verified.incrementAndGet();
                }
                if (created) {
                    newCount.incrementAndGet();
                    newFindings.add(raw);
                }
            }
            if (!newFindings.isEmpty()) {
                try {
                    notificationService.notifyNewFindings(repository, newFindings, settings);
                } catch (Exception ex) {
                    LOG.warn("Notification dispatch failed for {}/{}", repository.getProject().getKey(), repository.getSlug(), ex);
                }
            }
            run.setStatus(ScanStatus.SUCCESS.name());
        } catch (RuntimeException ex) {
            run.setStatus(ScanStatus.FAILED.name());
            run.setErrorMessage(truncate(ex.getMessage(), 4000));
            LOG.error("Scan failed for repo {}/{}",
                    repository.getProject().getKey(), repository.getSlug(), ex);
        } finally {
            long finishedAt = System.currentTimeMillis();
            run.setFinishedAt(finishedAt);
            run.setDurationMs(finishedAt - startedAt);
            run.setFindingsTotal(total.get());
            run.setFindingsVerified(verified.get());
            run.setFindingsNew(newCount.get());
            run.save();
        }
        return run;
    }

    /**
     * Run a scan across every repository the system can see. Intended to be
     * called by the scheduler under a clustered lock.
     */
    public void scanAllRepositories(ScanTrigger trigger) {
        TrufflehogSettings settings = configurationService.getSettings();
        int workers = Math.max(1, settings.getMaxConcurrentScans());
        ExecutorService pool = Executors.newFixedThreadPool(workers);

        EscalatedSecurityContext escalated = securityService.withPermission(
                Permission.SYS_ADMIN, "TruffleHog scheduled full scan");

        escalated.call(() -> {
            PageRequest req = new PageRequestImpl(0, 50);
            Page<? extends Repository> page;
            do {
                page = repositoryService.findAll(req);
                for (Repository repo : page.getValues()) {
                    if (!settings.isInScope(repo.getProject().getKey(), repo.getSlug())) {
                        LOG.debug("Skipping out-of-scope repo {}/{}", repo.getProject().getKey(), repo.getSlug());
                        continue;
                    }
                    pool.submit(() -> {
                        try {
                            scanRepository(repo, trigger, null, null, null);
                        } catch (Exception ex) {
                            LOG.warn("Scan failed for {}/{}",
                                    repo.getProject().getKey(), repo.getSlug(), ex);
                        }
                    });
                }
                req = page.getNextPageRequest();
            } while (page != null && !page.getIsLastPage() && req != null);
            return null;
        });

        pool.shutdown();
        try {
            pool.awaitTermination(12, TimeUnit.HOURS);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    private String currentUsername() {
        if (authenticationContext == null || authenticationContext.getCurrentUser() == null) {
            return "system";
        }
        return authenticationContext.getCurrentUser().getName();
    }

    private String truncate(String value, int limit) {
        if (value == null) {
            return "";
        }
        return value.length() <= limit ? value : value.substring(0, limit);
    }
}
