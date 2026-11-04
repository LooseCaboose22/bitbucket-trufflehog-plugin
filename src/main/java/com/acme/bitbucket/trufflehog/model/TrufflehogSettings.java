package com.acme.bitbucket.trufflehog.model;

/**
 * Global plugin configuration persisted via PluginSettingsFactory.
 */
public class TrufflehogSettings {
    private String trufflehogExecutable = "trufflehog";
    private String repositoriesRoot = "";
    private String extraArgs = "--only-verified";
    private String cronExpression = "0 0 2 * * ?";
    private int maxConcurrentScans = 2;
    private int scanTimeoutMinutes = 30;
    private boolean scanOnPush = true;
    private boolean blockPushOnVerifiedFinding = false;
    private boolean autoFingerprintRotation = true;
    private String allowlistRegex = "";

    // Detection filters
    private String verifiedFilter = "BOTH";
    private String detectorAllowlist = "";
    private String detectorIgnorelist = "";

    // Notifications — committer
    private boolean notifyCommitter = false;
    private String committerSubject = "[TruffleHog] Secret detected in your commit to {{repository}}";
    private String committerBody =
            "A secret was detected in a commit attributed to you.\n\n" +
            "Repository : {{repository}} (project {{project}})\n" +
            "Detector   : {{detector}}\n" +
            "File       : {{file}}\n" +
            "Branch     : {{branch}}\n" +
            "Commit     : {{commit}}\n\n" +
            "Please rotate this credential immediately and notify your security team.";

    // Notifications — administrators
    private boolean notifyAdmins = false;
    private String adminRecipients = "";
    private String adminSubject = "[TruffleHog] {{count}} secret(s) found in {{repository}}";

    // Notifications — infosec team
    private boolean notifyInfosec = false;
    private String infosecRecipients = "";
    private String infosecSubject = "[TruffleHog] Security alert: secrets detected in {{repository}}";

    // v1.1 — Direct SMTP (overrides Bitbucket mail server when smtpHost is non-empty)
    private String smtpHost = "";
    private int smtpPort = 587;
    private String smtpUser = "";
    private String smtpPass = "";
    private boolean smtpTls = true;
    private String smtpFromAddress = "";

    // v1.1 — Webhook notifications
    private String teamsWebhookUrl = "";
    private String slackWebhookUrl = "";

    // v1.1 — Notification mode
    private String notificationMode = "IMMEDIATE"; // IMMEDIATE | DIGEST
    private int digestHour = 8; // 0-23, hour of day to flush digest

    // v1.2 — Push result mode
    private String pushResultMode = "BLOCK"; // BLOCK | WARN | SILENT
    private String blockBranchPatterns = "main,master"; // comma-sep, exact match or glob *
    private String warnBranchPatterns = "";
    private String excludedPusherIds = ""; // Bitbucket usernames that skip scanning on push

    // v1.2 — Scan limits
    private int maxFileSizeKb = 0;   // 0 = unlimited
    private int maxTotalSizeMb = 0;  // 0 = unlimited

    // v1.4 — Custom detectors & advanced filtering
    private String warnOnlyDetectors = ""; // detector names that never block, only warn
    private String customDetectorConfigPath = ""; // path to TruffleHog --config file

    // v1.5 — Proxy
    private String proxyUrl = "";
    private String proxyNoProxy = "";
    private String proxyUser = "";
    private String proxyPass = "";
    private String proxySslCertPath = "";

    // v1.5 — Data Center per-node binary paths (JSON: {"nodeId": "/path/to/trufflehog"})
    private String perNodeBinaryPaths = "";

    // Scope
    private boolean enabledGlobally = true;
    private String enabledProjects = "";
    private String enabledRepositories = "";
    private String excludedProjects = "";
    private String excludedRepositories = "";

    // File exclusions
    private String excludedExtensions = ".class,.jar,.dll,.exe,.so,.bin,.zip,.tar,.gz,.7z,.rar,.png,.jpg,.jpeg,.gif,.ico,.svg,.woff,.woff2,.ttf,.eot";
    private String excludedDirectories = "vendor/,node_modules/";
    private String allowlistedPaths = "";
    private boolean enableGlobAllowlist = false;

    // ---- getters / setters ----

    public String getTrufflehogExecutable() { return trufflehogExecutable; }
    public void setTrufflehogExecutable(String v) { this.trufflehogExecutable = v; }
    public String getRepositoriesRoot() { return repositoriesRoot; }
    public void setRepositoriesRoot(String v) { this.repositoriesRoot = v; }
    public String getExtraArgs() { return extraArgs; }
    public void setExtraArgs(String v) { this.extraArgs = v; }
    public String getCronExpression() { return cronExpression; }
    public void setCronExpression(String v) { this.cronExpression = v; }
    public int getMaxConcurrentScans() { return maxConcurrentScans; }
    public void setMaxConcurrentScans(int v) { this.maxConcurrentScans = v; }
    public int getScanTimeoutMinutes() { return scanTimeoutMinutes; }
    public void setScanTimeoutMinutes(int v) { this.scanTimeoutMinutes = v; }
    public boolean isScanOnPush() { return scanOnPush; }
    public void setScanOnPush(boolean v) { this.scanOnPush = v; }
    public boolean isBlockPushOnVerifiedFinding() { return blockPushOnVerifiedFinding; }
    public void setBlockPushOnVerifiedFinding(boolean v) { this.blockPushOnVerifiedFinding = v; }
    public boolean isAutoFingerprintRotation() { return autoFingerprintRotation; }
    public void setAutoFingerprintRotation(boolean v) { this.autoFingerprintRotation = v; }
    public String getAllowlistRegex() { return allowlistRegex; }
    public void setAllowlistRegex(String v) { this.allowlistRegex = v; }

    public String getVerifiedFilter() { return verifiedFilter; }
    public void setVerifiedFilter(String v) { this.verifiedFilter = v; }
    public String getDetectorAllowlist() { return detectorAllowlist; }
    public void setDetectorAllowlist(String v) { this.detectorAllowlist = v; }
    public String getDetectorIgnorelist() { return detectorIgnorelist; }
    public void setDetectorIgnorelist(String v) { this.detectorIgnorelist = v; }

    public boolean isNotifyCommitter() { return notifyCommitter; }
    public void setNotifyCommitter(boolean v) { this.notifyCommitter = v; }
    public String getCommitterSubject() { return committerSubject; }
    public void setCommitterSubject(String v) { this.committerSubject = v; }
    public String getCommitterBody() { return committerBody; }
    public void setCommitterBody(String v) { this.committerBody = v; }
    public boolean isNotifyAdmins() { return notifyAdmins; }
    public void setNotifyAdmins(boolean v) { this.notifyAdmins = v; }
    public String getAdminRecipients() { return adminRecipients; }
    public void setAdminRecipients(String v) { this.adminRecipients = v; }
    public String getAdminSubject() { return adminSubject; }
    public void setAdminSubject(String v) { this.adminSubject = v; }
    public boolean isNotifyInfosec() { return notifyInfosec; }
    public void setNotifyInfosec(boolean v) { this.notifyInfosec = v; }
    public String getInfosecRecipients() { return infosecRecipients; }
    public void setInfosecRecipients(String v) { this.infosecRecipients = v; }
    public String getInfosecSubject() { return infosecSubject; }
    public void setInfosecSubject(String v) { this.infosecSubject = v; }

    public String getSmtpHost() { return smtpHost; }
    public void setSmtpHost(String v) { this.smtpHost = v; }
    public int getSmtpPort() { return smtpPort; }
    public void setSmtpPort(int v) { this.smtpPort = v; }
    public String getSmtpUser() { return smtpUser; }
    public void setSmtpUser(String v) { this.smtpUser = v; }
    public String getSmtpPass() { return smtpPass; }
    public void setSmtpPass(String v) { this.smtpPass = v; }
    public boolean isSmtpTls() { return smtpTls; }
    public void setSmtpTls(boolean v) { this.smtpTls = v; }
    public String getSmtpFromAddress() { return smtpFromAddress; }
    public void setSmtpFromAddress(String v) { this.smtpFromAddress = v; }

    public String getTeamsWebhookUrl() { return teamsWebhookUrl; }
    public void setTeamsWebhookUrl(String v) { this.teamsWebhookUrl = v; }
    public String getSlackWebhookUrl() { return slackWebhookUrl; }
    public void setSlackWebhookUrl(String v) { this.slackWebhookUrl = v; }
    public String getNotificationMode() { return notificationMode; }
    public void setNotificationMode(String v) { this.notificationMode = v; }
    public int getDigestHour() { return digestHour; }
    public void setDigestHour(int v) { this.digestHour = v; }

    public String getPushResultMode() { return pushResultMode; }
    public void setPushResultMode(String v) { this.pushResultMode = v; }
    public String getBlockBranchPatterns() { return blockBranchPatterns; }
    public void setBlockBranchPatterns(String v) { this.blockBranchPatterns = v; }
    public String getWarnBranchPatterns() { return warnBranchPatterns; }
    public void setWarnBranchPatterns(String v) { this.warnBranchPatterns = v; }
    public String getExcludedPusherIds() { return excludedPusherIds; }
    public void setExcludedPusherIds(String v) { this.excludedPusherIds = v; }
    public int getMaxFileSizeKb() { return maxFileSizeKb; }
    public void setMaxFileSizeKb(int v) { this.maxFileSizeKb = v; }
    public int getMaxTotalSizeMb() { return maxTotalSizeMb; }
    public void setMaxTotalSizeMb(int v) { this.maxTotalSizeMb = v; }

    public String getWarnOnlyDetectors() { return warnOnlyDetectors; }
    public void setWarnOnlyDetectors(String v) { this.warnOnlyDetectors = v; }
    public String getCustomDetectorConfigPath() { return customDetectorConfigPath; }
    public void setCustomDetectorConfigPath(String v) { this.customDetectorConfigPath = v; }

    public String getProxyUrl() { return proxyUrl; }
    public void setProxyUrl(String v) { this.proxyUrl = v; }
    public String getProxyNoProxy() { return proxyNoProxy; }
    public void setProxyNoProxy(String v) { this.proxyNoProxy = v; }
    public String getProxyUser() { return proxyUser; }
    public void setProxyUser(String v) { this.proxyUser = v; }
    public String getProxyPass() { return proxyPass; }
    public void setProxyPass(String v) { this.proxyPass = v; }
    public String getProxySslCertPath() { return proxySslCertPath; }
    public void setProxySslCertPath(String v) { this.proxySslCertPath = v; }

    public String getPerNodeBinaryPaths() { return perNodeBinaryPaths; }
    public void setPerNodeBinaryPaths(String v) { this.perNodeBinaryPaths = v; }

    public boolean isEnabledGlobally() { return enabledGlobally; }
    public void setEnabledGlobally(boolean v) { this.enabledGlobally = v; }
    public String getEnabledProjects() { return enabledProjects; }
    public void setEnabledProjects(String v) { this.enabledProjects = v; }
    public String getEnabledRepositories() { return enabledRepositories; }
    public void setEnabledRepositories(String v) { this.enabledRepositories = v; }
    public String getExcludedProjects() { return excludedProjects; }
    public void setExcludedProjects(String v) { this.excludedProjects = v; }
    public String getExcludedRepositories() { return excludedRepositories; }
    public void setExcludedRepositories(String v) { this.excludedRepositories = v; }

    public String getExcludedExtensions() { return excludedExtensions; }
    public void setExcludedExtensions(String v) { this.excludedExtensions = v; }
    public String getExcludedDirectories() { return excludedDirectories; }
    public void setExcludedDirectories(String v) { this.excludedDirectories = v; }
    public String getAllowlistedPaths() { return allowlistedPaths; }
    public void setAllowlistedPaths(String v) { this.allowlistedPaths = v; }
    public boolean isEnableGlobAllowlist() { return enableGlobAllowlist; }
    public void setEnableGlobAllowlist(boolean v) { this.enableGlobAllowlist = v; }

    /** True if this project+repo should be scanned given current scope settings. */
    public boolean isInScope(String projectKey, String repositorySlug) {
        String repoKey = projectKey + "/" + repositorySlug;
        if (enabledGlobally) {
            return !containsEntry(excludedProjects, projectKey)
                    && !containsEntry(excludedRepositories, repoKey);
        } else {
            return containsEntry(enabledProjects, projectKey)
                    || containsEntry(enabledRepositories, repoKey);
        }
    }

    /** True if this Bitbucket username should be skipped on push. */
    public boolean isPusherExcluded(String username) {
        return containsEntry(excludedPusherIds, username);
    }

    /** True if the branch matches any pattern in the comma-separated list (exact or glob *). */
    public static boolean matchesBranchPattern(String patterns, String branch) {
        if (patterns == null || patterns.trim().isEmpty() || branch == null) return false;
        for (String raw : patterns.split("[,;\\n\\r]+")) {
            String p = raw.trim();
            if (p.isEmpty()) continue;
            if (p.equals("*") || p.equalsIgnoreCase(branch)) return true;
            if (p.endsWith("*") && branch.startsWith(p.substring(0, p.length() - 1))) return true;
            if (p.startsWith("*") && branch.endsWith(p.substring(1))) return true;
        }
        return false;
    }

    private static boolean containsEntry(String list, String value) {
        if (list == null || list.trim().isEmpty() || value == null) return false;
        for (String entry : list.split("[,;\\n\\r]+")) {
            if (value.equalsIgnoreCase(entry.trim())) return true;
        }
        return false;
    }
}
