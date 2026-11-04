package com.acme.bitbucket.trufflehog.model;

/**
 * Parsed TruffleHog v3 JSON output line. Mapped onto a {@link SecretFinding}
 * by the scanner orchestrator.
 */
public class TruffleHogRawFinding {
    private String detectorType;
    private String detectorName;
    private boolean verified;
    private String redacted;
    private String raw;
    private String filePath;
    private int lineNumber;
    private String branch;
    private String commit;
    private String commitAuthor;
    private long commitTimestamp;
    private boolean warnOnly; // set by scanner for warn-only detectors

    public String getDetectorType() { return detectorType; }
    public void setDetectorType(String detectorType) { this.detectorType = detectorType; }
    public String getDetectorName() { return detectorName; }
    public void setDetectorName(String detectorName) { this.detectorName = detectorName; }
    public boolean isVerified() { return verified; }
    public void setVerified(boolean verified) { this.verified = verified; }
    public String getRedacted() { return redacted; }
    public void setRedacted(String redacted) { this.redacted = redacted; }
    public String getRaw() { return raw; }
    public void setRaw(String raw) { this.raw = raw; }
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
    public boolean isWarnOnly() { return warnOnly; }
    public void setWarnOnly(boolean warnOnly) { this.warnOnly = warnOnly; }
}
