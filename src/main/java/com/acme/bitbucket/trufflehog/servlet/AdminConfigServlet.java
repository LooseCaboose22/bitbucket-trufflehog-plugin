package com.acme.bitbucket.trufflehog.servlet;

import com.acme.bitbucket.trufflehog.ao.AoSecretAllowlistEntry;
import com.acme.bitbucket.trufflehog.model.TrufflehogSettings;
import com.acme.bitbucket.trufflehog.service.AllowlistService;
import com.acme.bitbucket.trufflehog.service.AuditService;
import com.acme.bitbucket.trufflehog.service.PluginPermissionService;
import com.acme.bitbucket.trufflehog.service.TrufflehogConfigurationService;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.templaterenderer.TemplateRenderer;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Renders the admin configuration form (TruffleHog binary path, cron, etc.).
 * Saves are performed via the REST endpoint from the form's JavaScript.
 */
@Named("adminConfigServlet")
@Component
public class AdminConfigServlet extends HttpServlet {

    private final TrufflehogConfigurationService configurationService;
    private final PluginPermissionService pluginPermissionService;
    private final TemplateRenderer templateRenderer;
    private final AllowlistService allowlistService;
    private final AuditService auditService;

    @Inject
    public AdminConfigServlet(TrufflehogConfigurationService configurationService,
                              PluginPermissionService pluginPermissionService,
                              @ComponentImport TemplateRenderer templateRenderer,
                              AllowlistService allowlistService,
                              AuditService auditService) {
        this.configurationService = configurationService;
        this.pluginPermissionService = pluginPermissionService;
        this.templateRenderer = templateRenderer;
        this.allowlistService = allowlistService;
        this.auditService = auditService;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (!pluginPermissionService.canAdminister()) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN,
                    "You must be a system administrator to configure the TruffleHog scanner.");
            return;
        }

        String cp = req.getContextPath();
        TrufflehogSettings settings = configurationService.getSettings();

        Map<String, Object> context = new HashMap<>();
        context.put("contextPath", cp);
        context.put("restBase", cp + "/rest/th-scanner/1.0/");
        context.put("trufflehogExecutable", settings.getTrufflehogExecutable());
        context.put("repositoriesRoot", settings.getRepositoriesRoot());
        context.put("extraArgs", settings.getExtraArgs());
        context.put("allowlistRegex", settings.getAllowlistRegex());
        context.put("cronExpression", settings.getCronExpression());
        context.put("maxConcurrentScans", settings.getMaxConcurrentScans());
        context.put("scanTimeoutMinutes", settings.getScanTimeoutMinutes());
        context.put("scanOnPush", settings.isScanOnPush());
        context.put("blockPushOnVerifiedFinding", settings.isBlockPushOnVerifiedFinding());
        context.put("verifiedFilter", settings.getVerifiedFilter());
        context.put("detectorAllowlist", settings.getDetectorAllowlist());
        context.put("detectorIgnorelist", settings.getDetectorIgnorelist());
        context.put("notifyCommitter", settings.isNotifyCommitter());
        context.put("committerSubject", settings.getCommitterSubject());
        context.put("committerBody", settings.getCommitterBody());
        context.put("notifyAdmins", settings.isNotifyAdmins());
        context.put("adminRecipients", settings.getAdminRecipients());
        context.put("adminSubject", settings.getAdminSubject());
        context.put("notifyInfosec", settings.isNotifyInfosec());
        context.put("infosecRecipients", settings.getInfosecRecipients());
        context.put("infosecSubject", settings.getInfosecSubject());

        // Scope
        context.put("enabledGlobally", settings.isEnabledGlobally());
        context.put("enabledProjects", settings.getEnabledProjects());
        context.put("enabledRepositories", settings.getEnabledRepositories());
        context.put("excludedProjects", settings.getExcludedProjects());
        context.put("excludedRepositories", settings.getExcludedRepositories());

        // File exclusions
        context.put("excludedExtensions", settings.getExcludedExtensions());
        context.put("excludedDirectories", settings.getExcludedDirectories());
        context.put("allowlistedPaths", settings.getAllowlistedPaths());
        context.put("enableGlobAllowlist", settings.isEnableGlobAllowlist());

        // Per-secret allowlist
        context.put("allowlistEntries", allowlistService.listAll());

        // v1.1 — Direct SMTP & webhooks
        context.put("smtpHost",         settings.getSmtpHost());
        context.put("smtpPort",         settings.getSmtpPort());
        context.put("smtpUser",         settings.getSmtpUser());
        context.put("smtpTls",          settings.isSmtpTls());
        context.put("smtpFromAddress",  settings.getSmtpFromAddress());
        context.put("teamsWebhookUrl",  settings.getTeamsWebhookUrl());
        context.put("slackWebhookUrl",  settings.getSlackWebhookUrl());
        context.put("notificationMode", settings.getNotificationMode());
        context.put("digestHour",       settings.getDigestHour());

        // v1.2 — Push result mode & scan limits
        context.put("pushResultMode",      settings.getPushResultMode());
        context.put("blockBranchPatterns", settings.getBlockBranchPatterns());
        context.put("warnBranchPatterns",  settings.getWarnBranchPatterns());
        context.put("excludedPusherIds",   settings.getExcludedPusherIds());
        context.put("maxFileSizeKb",       settings.getMaxFileSizeKb());
        context.put("maxTotalSizeMb",      settings.getMaxTotalSizeMb());

        // v1.4 — Custom detectors & audit
        context.put("warnOnlyDetectors",       settings.getWarnOnlyDetectors());
        context.put("customDetectorConfigPath", settings.getCustomDetectorConfigPath());
        context.put("auditLog",                auditService.listRecent(50));

        // v1.5 — Proxy & Data Center
        context.put("proxyUrl",          settings.getProxyUrl());
        context.put("proxyNoProxy",      settings.getProxyNoProxy());
        context.put("proxyUser",         settings.getProxyUser());
        context.put("proxySslCertPath",  settings.getProxySslCertPath());
        context.put("perNodeBinaryPaths", settings.getPerNodeBinaryPaths());

        resp.setContentType("text/html;charset=UTF-8");
        templateRenderer.render("templates/admin-config.vm", context, resp.getWriter());
    }
}
