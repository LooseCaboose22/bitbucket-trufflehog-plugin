package com.acme.bitbucket.trufflehog.ao;

import net.java.ao.Entity;
import net.java.ao.Preload;
import net.java.ao.schema.Indexed;
import net.java.ao.schema.NotNull;
import net.java.ao.schema.StringLength;
import net.java.ao.schema.Table;

@Preload
@Table("Finding")
public interface AoSecretFinding extends Entity {

    @Indexed
    @NotNull
    String getFingerprint();
    void setFingerprint(String fingerprint);

    @Indexed
    int getRepositoryId();
    void setRepositoryId(int repositoryId);

    @Indexed
    String getProjectKey();
    void setProjectKey(String projectKey);

    String getRepositorySlug();
    void setRepositorySlug(String repositorySlug);

    @StringLength(255)
    String getDetectorType();
    void setDetectorType(String detectorType);

    @StringLength(255)
    String getDetectorName();
    void setDetectorName(String detectorName);

    boolean isVerified();
    void setVerified(boolean verified);

    @StringLength(StringLength.UNLIMITED)
    String getRedacted();
    void setRedacted(String redacted);

    @StringLength(StringLength.UNLIMITED)
    String getFilePath();
    void setFilePath(String filePath);

    int getLineNumber();
    void setLineNumber(int lineNumber);

    @StringLength(255)
    String getBranch();
    void setBranch(String branch);

    @StringLength(80)
    String getCommit();
    void setCommit(String commit);

    @StringLength(255)
    String getCommitAuthor();
    void setCommitAuthor(String commitAuthor);

    long getCommitTimestamp();
    void setCommitTimestamp(long commitTimestamp);

    @StringLength(40)
    String getSeverity();
    void setSeverity(String severity);

    @Indexed
    @StringLength(40)
    @NotNull
    String getStatus();
    void setStatus(String status);

    @StringLength(StringLength.UNLIMITED)
    String getRemediationNote();
    void setRemediationNote(String remediationNote);

    @StringLength(255)
    String getResolvedBy();
    void setResolvedBy(String resolvedBy);

    long getResolvedAt();
    void setResolvedAt(long resolvedAt);

    long getDiscoveredAt();
    void setDiscoveredAt(long discoveredAt);

    @Indexed
    long getLastSeenAt();
    void setLastSeenAt(long lastSeenAt);

    int getOccurrenceCount();
    void setOccurrenceCount(int occurrenceCount);
}
