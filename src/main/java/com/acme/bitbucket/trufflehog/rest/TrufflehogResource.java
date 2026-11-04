package com.acme.bitbucket.trufflehog.rest;

import com.acme.bitbucket.trufflehog.ao.AoAuditLogEntry;
import com.acme.bitbucket.trufflehog.ao.AoSecretAllowlistEntry;
import com.acme.bitbucket.trufflehog.model.FindingStatus;
import com.acme.bitbucket.trufflehog.model.RemediationContext;
import com.acme.bitbucket.trufflehog.model.ScanScope;
import com.acme.bitbucket.trufflehog.model.ScanTrigger;
import com.acme.bitbucket.trufflehog.model.SecretFinding;
import com.acme.bitbucket.trufflehog.model.TrufflehogSettings;
import com.acme.bitbucket.trufflehog.scheduler.SchedulerBootstrap;
import com.acme.bitbucket.trufflehog.service.AllowlistService;
import com.acme.bitbucket.trufflehog.service.AuditService;
import com.acme.bitbucket.trufflehog.service.DashboardService;
import com.acme.bitbucket.trufflehog.service.FindingService;
import com.acme.bitbucket.trufflehog.service.NotificationService;
import com.acme.bitbucket.trufflehog.service.PluginPermissionService;
import com.acme.bitbucket.trufflehog.service.RemediationService;
import com.acme.bitbucket.trufflehog.service.ScanOrchestrator;
import com.acme.bitbucket.trufflehog.service.TrufflehogConfigurationService;
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.bitbucket.repository.RepositoryService;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.DELETE;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Named
@Component
@Path("/")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class TrufflehogResource {
    private static final Logger LOG = LoggerFactory.getLogger(TrufflehogResource.class);
    private static final int MAX_PAGE_SIZE = 500;

    private static final ObjectMapper JACKSON2 = new ObjectMapper();

    private static final ExecutorService SCAN_EXECUTOR = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "trufflehog-rest-scan");
        t.setDaemon(true);
        return t;
    });

    private final FindingService findingService;
    private final RemediationService remediationService;
    private final DashboardService dashboardService;
    private final ScanOrchestrator scanOrchestrator;
    private final TrufflehogConfigurationService configurationService;
    private final SchedulerBootstrap schedulerBootstrap;
    private final PluginPermissionService pluginPermissionService;
    private final RepositoryService repositoryService;
    private final AllowlistService allowlistService;
    private final AuditService auditService;
    private final NotificationService notificationService;

    @Inject
    public TrufflehogResource(
            FindingService findingService,
            RemediationService remediationService,
            DashboardService dashboardService,
            ScanOrchestrator scanOrchestrator,
            TrufflehogConfigurationService configurationService,
            SchedulerBootstrap schedulerBootstrap,
            PluginPermissionService pluginPermissionService,
            @ComponentImport RepositoryService repositoryService,
            AllowlistService allowlistService,
            AuditService auditService,
            NotificationService notificationService) {
        this.findingService = findingService;
        this.remediationService = remediationService;
        this.dashboardService = dashboardService;
        this.scanOrchestrator = scanOrchestrator;
        this.configurationService = configurationService;
        this.schedulerBootstrap = schedulerBootstrap;
        this.pluginPermissionService = pluginPermissionService;
        this.repositoryService = repositoryService;
        this.allowlistService = allowlistService;
        this.auditService = auditService;
        this.notificationService = notificationService;
    }

    // ---------- Dashboards ----------

    @GET
    @Path("dashboard/{scope}")
    public Response dashboard(@PathParam("scope") String scope,
                              @QueryParam("projectKey") String projectKey,
                              @QueryParam("repositorySlug") String repositorySlug) {
        ScanScope normalized;
        try {
            normalized = ScanScope.valueOf(scope.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return error(Response.Status.BAD_REQUEST, "Unknown scope: " + scope);
        }
        Response denial = checkRead(normalized, projectKey, repositorySlug);
        if (denial != null) {
            return denial;
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("summary", pojoToMap(dashboardService.summarize(scopeProject(normalized, projectKey),
                scopeRepo(normalized, repositorySlug))));
        body.put("scanRuns", pojoListToMaps(dashboardService.recentScanRuns(scopeProject(normalized, projectKey),
                scopeRepo(normalized, repositorySlug), 25)));
        if (normalized == ScanScope.SYSTEM) {
            body.put("projects", pojoListToMaps(dashboardService.projectRollups()));
            body.put("topRepositories", pojoListToMaps(dashboardService.topRepositories(null, 25)));
        } else if (normalized == ScanScope.PROJECT) {
            body.put("topRepositories", pojoListToMaps(dashboardService.topRepositories(projectKey, 25)));
        }
        return Response.ok(body).build();
    }

    // ---------- Findings ----------

    @GET
    @Path("findings")
    public Response listFindings(@QueryParam("projectKey") String projectKey,
                                  @QueryParam("repositorySlug") String repositorySlug,
                                  @QueryParam("status") String status,
                                  @QueryParam("detectorType") String detectorType,
                                  @QueryParam("onlyVerified") @DefaultValue("false") boolean onlyVerified,
                                  @QueryParam("sortBy") @DefaultValue("lastSeenAt") String sortBy,
                                  @QueryParam("sortDirection") @DefaultValue("desc") String sortDirection,
                                  @QueryParam("limit") @DefaultValue("200") int limit,
                                  @QueryParam("offset") @DefaultValue("0") int offset) {
        Response denial = checkRead(scopeFor(projectKey, repositorySlug), projectKey, repositorySlug);
        if (denial != null) {
            return denial;
        }

        if (!isAllowedSortBy(sortBy)) {
            return error(Response.Status.BAD_REQUEST, "invalid sortBy");
        }
        if (!isAllowedSortDirection(sortDirection)) {
            return error(Response.Status.BAD_REQUEST, "invalid sortDirection");
        }
        if (status != null && !status.isEmpty() && !isAllowedStatus(status)) {
            return error(Response.Status.BAD_REQUEST, "invalid status");
        }

        FindingService.FindingFilter filter = new FindingService.FindingFilter();
        filter.projectKey = projectKey;
        filter.repositorySlug = repositorySlug;
        if (status != null && !status.isEmpty()) {
            filter.status = FindingStatus.fromString(status);
        }
        filter.detectorType = detectorType;
        filter.onlyVerified = onlyVerified;
        filter.sortBy = sortBy;
        filter.sortDirection = sortDirection;
        filter.limit = sanitizeLimit(limit);
        filter.offset = sanitizeOffset(offset);

        List<SecretFinding> findings = findingService.list(filter);
        List<Map<String, Object>> findingMaps = new ArrayList<>();
        for (SecretFinding f : findings) {
            findingMaps.add(findingToMap(f));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("total", findingService.count(filter));
        body.put("findings", findingMaps);
        return Response.ok(body).build();
    }

    @GET
    @Path("findings/{id}")
    public Response findingDetail(@PathParam("id") int id) {
        SecretFinding f = findingService.findById(id);
        if (f == null) {
            return error(Response.Status.NOT_FOUND, "finding not found");
        }
        if (!pluginPermissionService.canViewRepository(f.getProjectKey(), f.getRepositorySlug())) {
            return error(Response.Status.FORBIDDEN, "not permitted");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("finding", findingToMap(f));
        body.put("history", remediationService.history(id));
        return Response.ok(body).build();
    }

    @PUT
    @Path("findings/{id}/status")
    public Response updateStatus(@PathParam("id") int id,
                                 Map<String, String> payload,
                                 @Context HttpServletRequest request) {
        SecretFinding existing = findingService.findById(id);
        if (existing == null) {
            return error(Response.Status.NOT_FOUND, "finding not found");
        }
        if (!pluginPermissionService.canRemediateRepository(existing.getProjectKey(),
                existing.getRepositorySlug())) {
            return error(Response.Status.FORBIDDEN, "not permitted");
        }
        FindingStatus status = FindingStatus.fromString(payload == null ? null : payload.get("status"));
        String note = payload == null ? "" : payload.getOrDefault("remediationNote", "");
        if (note == null) {
            note = "";
        }
        if (status != FindingStatus.OPEN && note.trim().length() < 8) {
            return error(Response.Status.BAD_REQUEST,
                    "remediationNote must be at least 8 characters for non-OPEN statuses");
        }

        RemediationContext context = new RemediationContext();
        if (request != null) {
            context.setSourceIp(request.getRemoteAddr());
            context.setUserAgent(request.getHeader("User-Agent"));
        }

        boolean ok = remediationService.transition(id, status, note, context);
        if (!ok) {
            return error(Response.Status.NOT_FOUND, "finding not found");
        }
        return Response.ok(Map.of("status", "updated", "newStatus", status.name())).build();
    }

    @GET
    @Path("findings/{id}/history")
    public Response history(@PathParam("id") int id) {
        SecretFinding existing = findingService.findById(id);
        if (existing == null) {
            return error(Response.Status.NOT_FOUND, "finding not found");
        }
        if (!pluginPermissionService.canViewRepository(existing.getProjectKey(),
                existing.getRepositorySlug())) {
            return error(Response.Status.FORBIDDEN, "not permitted");
        }
        return Response.ok(remediationService.history(id)).build();
    }

    // ---------- Scans ----------

    @POST
    @Path("scan/all")
    public Response scanAll() {
        if (!pluginPermissionService.canAdminister()) {
            return error(Response.Status.FORBIDDEN, "system admin required");
        }
        SCAN_EXECUTOR.submit(() -> {
            try {
                scanOrchestrator.scanAllRepositories(ScanTrigger.MANUAL);
            } catch (Exception ex) {
                LOG.warn("Manual full-system scan failed", ex);
            }
        });
        return Response.status(Response.Status.ACCEPTED)
            .entity(Map.of("status", "queued"))
            .build();
    }

    @POST
    @Path("scan/project/{projectKey}")
    public Response scanProject(@PathParam("projectKey") String projectKey) {
        if (!pluginPermissionService.canRemediateProject(projectKey)) {
            return error(Response.Status.FORBIDDEN, "project admin required");
        }
        SCAN_EXECUTOR.submit(() -> {
            try {
                Iterable<Repository> repos = repositoryService.findByProjectKey(projectKey,
                    new com.atlassian.bitbucket.util.PageRequestImpl(0, 500)).getValues();
                for (Repository repo : repos) {
                    scanOrchestrator.scanRepository(repo, ScanTrigger.MANUAL, null, null, null);
                }
            } catch (Exception ex) {
                LOG.warn("Manual project scan failed for {}", projectKey, ex);
            }
        });
        return Response.status(Response.Status.ACCEPTED)
            .entity(Map.of("status", "queued", "projectKey", projectKey))
            .build();
    }

    @POST
    @Path("scan/repository/{projectKey}/{repositorySlug}")
    public Response scanRepository(@PathParam("projectKey") String projectKey,
                                    @PathParam("repositorySlug") String repositorySlug) {
        if (!pluginPermissionService.canRemediateRepository(projectKey, repositorySlug)) {
            return error(Response.Status.FORBIDDEN, "repository admin required");
        }
        Repository repo = repositoryService.getBySlug(projectKey, repositorySlug);
        if (repo == null) {
            return error(Response.Status.NOT_FOUND, "repository not found");
        }
        final Repository r = repo;
        SCAN_EXECUTOR.submit(() -> {
            try {
                scanOrchestrator.scanRepository(r, ScanTrigger.MANUAL, null, null, null);
            } catch (Exception ex) {
                LOG.warn("Manual repository scan failed for {}/{}", projectKey, repositorySlug, ex);
            }
        });
        return Response.status(Response.Status.ACCEPTED)
            .entity(Map.of("status", "queued",
                "projectKey", projectKey, "repositorySlug", repositorySlug))
            .build();
    }

    // ---------- Settings ----------

    @GET
    @Path("settings")
    public Response getSettings() {
        if (!pluginPermissionService.canAdminister()) {
            return error(Response.Status.FORBIDDEN, "system admin required");
        }
        return Response.ok(pojoToMap(configurationService.getSettings())).build();
    }

    @PUT
    @Path("settings")
    public Response updateSettings(TrufflehogSettings settings) {
        if (!pluginPermissionService.canAdminister()) {
            return error(Response.Status.FORBIDDEN, "system admin required");
        }
        configurationService.saveSettings(settings);
        schedulerBootstrap.reschedule();
        return Response.ok(Map.of("status", "saved")).build();
    }

    // ---------- helpers ----------

    private ScanScope scopeFor(String projectKey, String repositorySlug) {
        if (repositorySlug != null && !repositorySlug.isEmpty()) {
            return ScanScope.REPOSITORY;
        }
        if (projectKey != null && !projectKey.isEmpty()) {
            return ScanScope.PROJECT;
        }
        return ScanScope.SYSTEM;
    }

    private String scopeProject(ScanScope scope, String projectKey) {
        return (scope == ScanScope.PROJECT || scope == ScanScope.REPOSITORY) ? projectKey : null;
    }

    private String scopeRepo(ScanScope scope, String repositorySlug) {
        return scope == ScanScope.REPOSITORY ? repositorySlug : null;
    }

    private Response checkRead(ScanScope scope, String projectKey, String repositorySlug) {
        boolean ok;
        switch (scope) {
            case REPOSITORY:
                ok = pluginPermissionService.canViewRepository(projectKey, repositorySlug);
                break;
            case PROJECT:
                ok = pluginPermissionService.canViewProject(projectKey);
                break;
            case SYSTEM:
            default:
                ok = pluginPermissionService.canViewSystemDashboard();
                break;
        }
        return ok ? null : error(Response.Status.FORBIDDEN, "not permitted");
    }

    private Response error(Response.Status status, String message) {
        return Response.status(status)
                .entity(Map.of("error", message))
                .build();
    }

    private int sanitizeLimit(int limit) {
        if (limit <= 0) {
            return 50;
        }
        return Math.min(limit, MAX_PAGE_SIZE);
    }

    private int sanitizeOffset(int offset) {
        return Math.max(offset, 0);
    }

    private boolean isAllowedStatus(String status) {
        String normalized = status.trim().toUpperCase();
        return "OPEN".equals(normalized)
                || "FIXED".equals(normalized)
                || "FALSE_POSITIVE".equals(normalized)
                || "ACCEPTED_RISK".equals(normalized);
    }

    private boolean isAllowedSortDirection(String sortDirection) {
        String normalized = sortDirection == null ? "" : sortDirection.trim().toLowerCase();
        return "asc".equals(normalized) || "desc".equals(normalized);
    }

    private boolean isAllowedSortBy(String sortBy) {
        if (sortBy == null) {
            return false;
        }
        String normalized = sortBy.trim().toLowerCase();
        return "lastseenat".equals(normalized)
                || "discoveredat".equals(normalized)
                || "severity".equals(normalized)
                || "status".equals(normalized)
                || "detectortype".equals(normalized)
                || "projectkey".equals(normalized)
                || "repositoryslug".equals(normalized);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> pojoToMap(Object pojo) {
        if (pojo == null) {
            return new LinkedHashMap<>();
        }
        try {
            return JACKSON2.convertValue(pojo, Map.class);
        } catch (Exception e) {
            LOG.warn("Failed to convert POJO to map: {}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    private List<Map<String, Object>> pojoListToMaps(List<?> list) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (list != null) {
            for (Object item : list) {
                result.add(pojoToMap(item));
            }
        }
        return result;
    }

    private Map<String, Object> findingToMap(SecretFinding f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", f.getId());
        m.put("fingerprint", f.getFingerprint());
        m.put("repositoryId", f.getRepositoryId());
        m.put("projectKey", f.getProjectKey());
        m.put("repositorySlug", f.getRepositorySlug());
        m.put("detectorType", f.getDetectorType());
        m.put("detectorName", f.getDetectorName());
        m.put("verified", f.isVerified());
        m.put("redacted", f.getRedacted());
        m.put("filePath", f.getFilePath());
        m.put("lineNumber", f.getLineNumber());
        m.put("branch", f.getBranch());
        m.put("commit", f.getCommit());
        m.put("commitAuthor", f.getCommitAuthor());
        m.put("commitTimestamp", f.getCommitTimestamp());
        m.put("severity", f.getSeverity());
        m.put("status", f.getStatus());
        m.put("remediationNote", f.getRemediationNote());
        m.put("resolvedBy", f.getResolvedBy());
        m.put("resolvedAt", f.getResolvedAt());
        m.put("discoveredAt", f.getDiscoveredAt());
        m.put("lastSeenAt", f.getLastSeenAt());
        m.put("occurrenceCount", f.getOccurrenceCount());
        return m;
    }

    // ---------- Audit log ----------

    @GET
    @Path("audit")
    public Response listAudit(@QueryParam("limit") @DefaultValue("100") int limit) {
        if (!pluginPermissionService.canAdminister()) {
            return error(Response.Status.FORBIDDEN, "system admin required");
        }
        List<AoAuditLogEntry> entries = auditService.listRecent(limit);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (AoAuditLogEntry e : entries) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e.getID());
            m.put("action", e.getAction());
            m.put("actor", e.getActor());
            m.put("entityType", e.getEntityType());
            m.put("entityId", e.getEntityId());
            m.put("detail", e.getDetail());
            m.put("occurredAt", e.getOccurredAt());
            rows.add(m);
        }
        return Response.ok(rows).build();
    }

    // ---------- Notification test ----------

    @POST
    @Path("notifications/test")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response testNotification(@Context HttpServletRequest req) {
        if (!pluginPermissionService.canAdminister()) {
            return error(Response.Status.FORBIDDEN, "system admin required");
        }
        TrufflehogSettings settings = configurationService.getSettings();
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = JACKSON2.readValue(req.getInputStream(), Map.class);
            String type = str(body, "type");
            if ("email".equalsIgnoreCase(type)) {
                String to = str(body, "to");
                if (to == null || to.trim().isEmpty()) {
                    return error(Response.Status.BAD_REQUEST, "to is required for email test");
                }
                boolean ok = notificationService.sendTestEmail(to.trim(), settings);
                auditService.log("TEST_EMAIL", req.getRemoteUser(), "notification", to, "result=" + ok);
                return Response.ok(Map.of("status", ok ? "sent" : "failed")).build();
            } else if ("teams".equalsIgnoreCase(type)) {
                boolean ok = notificationService.sendTestTeamsWebhook(settings);
                auditService.log("TEST_TEAMS", req.getRemoteUser(), "notification", "teams", "result=" + ok);
                return Response.ok(Map.of("status", ok ? "sent" : "failed")).build();
            } else if ("slack".equalsIgnoreCase(type)) {
                boolean ok = notificationService.sendTestSlackWebhook(settings);
                auditService.log("TEST_SLACK", req.getRemoteUser(), "notification", "slack", "result=" + ok);
                return Response.ok(Map.of("status", ok ? "sent" : "failed")).build();
            } else {
                return error(Response.Status.BAD_REQUEST, "type must be 'email', 'teams', or 'slack'");
            }
        } catch (Exception ex) {
            LOG.warn("Test notification failed", ex);
            return error(Response.Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        }
    }

    // ---------- Config export / import ----------

    @GET
    @Path("config/export")
    @Produces(MediaType.APPLICATION_JSON)
    public Response exportConfig() {
        if (!pluginPermissionService.canAdminister()) {
            return error(Response.Status.FORBIDDEN, "system admin required");
        }
        TrufflehogSettings settings = configurationService.getSettings();
        // Omit sensitive credentials from export
        Map<String, Object> export = pojoToMap(settings);
        export.remove("smtpPass");
        export.remove("proxyPass");
        return Response.ok(export)
                .header("Content-Disposition", "attachment; filename=\"trufflehog-config.json\"")
                .build();
    }

    @POST
    @Path("config/import")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response importConfig(@Context HttpServletRequest req) {
        if (!pluginPermissionService.canAdminister()) {
            return error(Response.Status.FORBIDDEN, "system admin required");
        }
        try {
            TrufflehogSettings incoming = JACKSON2.readValue(req.getInputStream(), TrufflehogSettings.class);
            // Preserve passwords from current settings if not supplied in import
            TrufflehogSettings current = configurationService.getSettings();
            if (incoming.getSmtpPass() == null || incoming.getSmtpPass().isEmpty()) {
                incoming.setSmtpPass(current.getSmtpPass());
            }
            if (incoming.getProxyPass() == null || incoming.getProxyPass().isEmpty()) {
                incoming.setProxyPass(current.getProxyPass());
            }
            configurationService.saveSettings(incoming);
            schedulerBootstrap.reschedule();
            auditService.log("CONFIG_IMPORT", req.getRemoteUser(), "settings", "global", "imported via REST");
            return Response.ok(Map.of("status", "imported")).build();
        } catch (Exception ex) {
            LOG.warn("Config import failed", ex);
            return error(Response.Status.BAD_REQUEST, "Invalid config JSON: " + ex.getMessage());
        }
    }

    // ---------- Per-secret allowlist ----------

    @GET
    @Path("allowlist")
    public Response listAllowlist() {
        if (!pluginPermissionService.canAdminister()) return error(Response.Status.FORBIDDEN, "system admin required");
        List<Map<String, Object>> rows = new ArrayList<>();
        for (AoSecretAllowlistEntry e : allowlistService.listAll()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e.getID());
            m.put("fingerprint", e.getFingerprint());
            m.put("detectorName", e.getDetectorName());
            m.put("redacted", e.getRedacted());
            m.put("reason", e.getReason());
            m.put("addedBy", e.getAddedBy());
            m.put("addedAt", e.getAddedAt());
            rows.add(m);
        }
        return Response.ok(rows).build();
    }

    @POST
    @Path("allowlist")
    public Response addAllowlist(@Context HttpServletRequest req) {
        if (!pluginPermissionService.canAdminister()) return error(Response.Status.FORBIDDEN, "system admin required");
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = JACKSON2.readValue(req.getInputStream(), Map.class);
            String fingerprint  = str(body, "fingerprint");
            String detectorName = str(body, "detectorName");
            String redacted     = str(body, "redacted");
            String reason       = str(body, "reason");
            if (fingerprint == null || fingerprint.trim().isEmpty()) {
                return error(Response.Status.BAD_REQUEST, "fingerprint is required");
            }
            String addedBy = req.getRemoteUser() != null ? req.getRemoteUser() : "admin";
            AoSecretAllowlistEntry entry = allowlistService.add(
                    fingerprint.trim(), detectorName, redacted, reason, addedBy);
            Map<String, Object> m = new LinkedHashMap<>();
            if (entry != null) {
                m.put("id", entry.getID());
                m.put("fingerprint", entry.getFingerprint());
            }
            return Response.status(Response.Status.CREATED).entity(m).build();
        } catch (Exception ex) {
            LOG.warn("Failed to add allowlist entry", ex);
            return error(Response.Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        }
    }

    @DELETE
    @Path("allowlist/{id}")
    public Response removeAllowlist(@PathParam("id") int id) {
        if (!pluginPermissionService.canAdminister()) return error(Response.Status.FORBIDDEN, "system admin required");
        boolean removed = allowlistService.remove(id);
        return removed
                ? Response.noContent().build()
                : error(Response.Status.NOT_FOUND, "Entry not found: " + id);
    }

    private String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v == null ? null : v.toString();
    }
}
