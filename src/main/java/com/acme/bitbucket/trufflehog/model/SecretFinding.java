package com.acme.bitbucket.trufflehog.model;

/**
 * REST/DTO representation of a secret finding. Persistent record lives in
 * {@code com.acme.bitbucket.trufflehog.ao.AoSecretFinding}.
 */
public class SecretFinding {
    private int id;
    private String fingerprint;
    private int repositoryId;
    private String projectKey;
    private String repositorySlug;
    private String detectorType;
    private String detectorName;
    private boolean verified;
    private String redacted;
    private String filePath;
    private int lineNumber;
    private String branch;
    private String commit;
    private String commitAuthor;
    private long commitTimestamp;
    private String severity;
    private String status;
    private String remediationNote;
    private String resolvedBy;
    private long resolvedAt;
    private long discoveredAt;
    private long lastSeenAt;
    private int occurrenceCount;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getFingerprint() { return fingerprint; }
    public void setFingerprint(String fingerprint) { this.fingerprint = fingerprint; }
    public int getRepositoryId() { return repositoryId; }
    public void setRepositoryId(int repositoryId) { this.repositoryId = repositoryId; }
    public String getProjectKey() { return projectKey; }
    public void setProjectKey(String projectKey) { this.projectKey = projectKey; }
    public String getRepositorySlug() { return repositorySlug; }
    public void setRepositorySlug(String repositorySlug) { this.repositorySlug = repositorySlug; }
    public String getDetectorType() { return detectorType; }
    public void setDetectorType(String detectorType) { this.detectorType = detectorType; }
    public String getDetectorName() { return detectorName; }
    public void setDetectorName(String detectorName) { this.detectorName = detectorName; }
    public boolean isVerified() { return verified; }
    public void setVerified(boolean verified) { this.verified = verified; }
    public String getRedacted() { return redacted; }
    public void setRedacted(String redacted) { this.redacted = redacted; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public int getLineNumber() { return lineNumber; }
    public void setLineNumber(int lineNumber) { this.lineNumber = lineNumber; }
    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }
    public String getCommit() { return commit; }
    public void setCommit(String commit) { this.commit = commit; }
    public String getCommitAuthor() { return commitAuthor; }
    public void setCommitAuthor(String commitAuthor) { this.commitAuthor = commitAuthor; }
    public long getCommitTimestamp() { return commitTimestamp; }
    public void setCommitTimestamp(long commitTimestamp) { this.commitTimestamp = commitTimestamp; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRemediationNote() { return remediationNote; }
    public void setRemediationNote(String remediationNote) { this.remediationNote = remediationNote; }
    public String getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(String resolvedBy) { this.resolvedBy = resolvedBy; }
    public long getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(long resolvedAt) { this.resolvedAt = resolvedAt; }
    public long getDiscoveredAt() { return discoveredAt; }
    public void setDiscoveredAt(long discoveredAt) { this.discoveredAt = discoveredAt; }
    public long getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(long lastSeenAt) { this.lastSeenAt = lastSeenAt; }
    public int getOccurrenceCount() { return occurrenceCount; }
    public void setOccurrenceCount(int occurrenceCount) { this.occurrenceCount = occurrenceCount; }
}
