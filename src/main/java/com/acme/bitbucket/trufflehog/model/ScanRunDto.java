package com.acme.bitbucket.trufflehog.model;

public class ScanRunDto {
    private int id;
    private int repositoryId;
    private String projectKey;
    private String repositorySlug;
    private String triggerType;
    private String triggeredBy;
    private long startedAt;
    private long finishedAt;
    private long durationMs;
    private String status;
    private int findingsTotal;
    private int findingsVerified;
    private int findingsNew;
    private String errorMessage;
    private String refName;
    private String fromCommit;
    private String toCommit;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getRepositoryId() { return repositoryId; }
    public void setRepositoryId(int repositoryId) { this.repositoryId = repositoryId; }
    public String getProjectKey() { return projectKey; }
    public void setProjectKey(String projectKey) { this.projectKey = projectKey; }
    public String getRepositorySlug() { return repositorySlug; }
    public void setRepositorySlug(String repositorySlug) { this.repositorySlug = repositorySlug; }
    public String getTriggerType() { return triggerType; }
    public void setTriggerType(String triggerType) { this.triggerType = triggerType; }
    public String getTriggeredBy() { return triggeredBy; }
    public void setTriggeredBy(String triggeredBy) { this.triggeredBy = triggeredBy; }
    public long getStartedAt() { return startedAt; }
    public void setStartedAt(long startedAt) { this.startedAt = startedAt; }
    public long getFinishedAt() { return finishedAt; }
    public void setFinishedAt(long finishedAt) { this.finishedAt = finishedAt; }
    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getFindingsTotal() { return findingsTotal; }
    public void setFindingsTotal(int findingsTotal) { this.findingsTotal = findingsTotal; }
    public int getFindingsVerified() { return findingsVerified; }
    public void setFindingsVerified(int findingsVerified) { this.findingsVerified = findingsVerified; }
    public int getFindingsNew() { return findingsNew; }
    public void setFindingsNew(int findingsNew) { this.findingsNew = findingsNew; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getRefName() { return refName; }
    public void setRefName(String refName) { this.refName = refName; }
    public String getFromCommit() { return fromCommit; }
    public void setFromCommit(String fromCommit) { this.fromCommit = fromCommit; }
    public String getToCommit() { return toCommit; }
    public void setToCommit(String toCommit) { this.toCommit = toCommit; }
}
