package com.acme.bitbucket.trufflehog.hook;

import com.acme.bitbucket.trufflehog.model.ScanTrigger;
import com.acme.bitbucket.trufflehog.model.TrufflehogSettings;
import com.acme.bitbucket.trufflehog.service.AuditService;
import com.acme.bitbucket.trufflehog.service.ScanOrchestrator;
import com.acme.bitbucket.trufflehog.service.TrufflehogConfigurationService;
import com.atlassian.bitbucket.hook.repository.PostRepositoryHook;
import com.atlassian.bitbucket.hook.repository.PostRepositoryHookContext;
import com.atlassian.bitbucket.hook.repository.RepositoryHookRequest;
import com.atlassian.bitbucket.repository.RefChange;
import com.atlassian.bitbucket.repository.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

@Named("postReceiveScanHook")
@Component
public class PostReceiveScanHook implements PostRepositoryHook<RepositoryHookRequest> {
    private static final Logger LOG = LoggerFactory.getLogger(PostReceiveScanHook.class);

    private static final ExecutorService SCAN_POOL = Executors.newFixedThreadPool(
            Math.max(2, Runtime.getRuntime().availableProcessors() / 2),
            new ThreadFactory() {
                private final AtomicInteger counter = new AtomicInteger();
                @Override public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "trufflehog-post-receive-" + counter.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                }
            });

    private final ScanOrchestrator scanOrchestrator;
    private final TrufflehogConfigurationService configurationService;
    private final AuditService auditService;

    @Inject
    public PostReceiveScanHook(ScanOrchestrator scanOrchestrator,
                                TrufflehogConfigurationService configurationService,
                                AuditService auditService) {
        this.scanOrchestrator = scanOrchestrator;
        this.configurationService = configurationService;
        this.auditService = auditService;
    }

    @Override
    public void postUpdate(PostRepositoryHookContext context, RepositoryHookRequest request) {
        TrufflehogSettings settings = configurationService.getSettings();

        if (!settings.isScanOnPush()) return;

        final Repository repository = request.getRepository();
        if (!settings.isInScope(repository.getProject().getKey(), repository.getSlug())) return;

        // v1.2 — skip excluded pushers
        String pusher = request.getContext() != null ? request.getContext().toString() : null;
        // Bitbucket doesn't expose the pusher username directly on RepositoryHookRequest;
        // we check what we can via the hook context attributes.
        // The principal is available via PostRepositoryHookContext if it extends ApplicationUser context.
        // For now we check the displayName from the context.
        try {
            Object principal = context.getClass().getMethod("getTrigger").invoke(context);
            if (principal != null) {
                String name = principal.toString();
                if (settings.isPusherExcluded(name)) {
                    LOG.debug("Skipping push scan — pusher {} is excluded", name);
                    return;
                }
            }
        } catch (Exception ignored) {}

        // v1.2 — SILENT mode skips push scanning entirely
        if ("SILENT".equalsIgnoreCase(settings.getPushResultMode())) {
            LOG.debug("Push scan skipped — pushResultMode=SILENT for {}/{}",
                    repository.getProject().getKey(), repository.getSlug());
            return;
        }

        for (RefChange change : request.getRefChanges()) {
            final String ref     = change.getRef() == null ? "" : change.getRef().getId();
            final String fromHash = change.getFromHash();
            final String toHash   = change.getToHash();

            // v1.2 — check branch patterns for push result mode
            String branchName = ref.startsWith("refs/heads/") ? ref.substring(11) : ref;
            final String effectiveMode = resolveEffectiveMode(settings, branchName);

            SCAN_POOL.submit(() -> {
                try {
                    scanOrchestrator.scanRepository(repository, ScanTrigger.POST_RECEIVE,
                            ref, fromHash, toHash);
                    auditService.log("PUSH_SCAN", "hook", "repository",
                            repository.getProject().getKey() + "/" + repository.getSlug(),
                            "ref=" + ref + " mode=" + effectiveMode);
                } catch (Exception ex) {
                    LOG.warn("Post-receive scan failed for {}/{} ref {}",
                            repository.getProject().getKey(), repository.getSlug(), ref, ex);
                }
            });
        }
    }

    private String resolveEffectiveMode(TrufflehogSettings settings, String branch) {
        String mode = settings.getPushResultMode();
        if ("BLOCK".equalsIgnoreCase(mode)) {
            // If branch matches warn-only patterns, downgrade to WARN
            if (TrufflehogSettings.matchesBranchPattern(settings.getWarnBranchPatterns(), branch)) {
                return "WARN";
            }
            // If branch doesn't match block patterns, downgrade to WARN
            if (!TrufflehogSettings.matchesBranchPattern(settings.getBlockBranchPatterns(), branch)) {
                return "WARN";
            }
        }
        return mode;
    }
}
