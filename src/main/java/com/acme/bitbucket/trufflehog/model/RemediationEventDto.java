package com.acme.bitbucket.trufflehog.model;

public class RemediationEventDto {
    private int id;
    private int findingId;
    private String fromStatus;
    private String toStatus;
    private String actor;
    private String note;
    private String projectKey;
    private String repositorySlug;
    private String sourceIp;
    private String userAgent;
    private long occurredAt;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getFindingId() { return findingId; }
    public void setFindingId(int findingId) { this.findingId = findingId; }
    public String getFromStatus() { return fromStatus; }
    public void setFromStatus(String fromStatus) { this.fromStatus = fromStatus; }
    public String getToStatus() { return toStatus; }
    public void setToStatus(String toStatus) { this.toStatus = toStatus; }
    public String getActor() { return actor; }
    public void setActor(String actor) { this.actor = actor; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public String getProjectKey() { return projectKey; }
    public void setProjectKey(String projectKey) { this.projectKey = projectKey; }
    public String getRepositorySlug() { return repositorySlug; }
    public void setRepositorySlug(String repositorySlug) { this.repositorySlug = repositorySlug; }
    public String getSourceIp() { return sourceIp; }
    public void setSourceIp(String sourceIp) { this.sourceIp = sourceIp; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    public long getOccurredAt() { return occurredAt; }
    public void setOccurredAt(long occurredAt) { this.occurredAt = occurredAt; }
}
