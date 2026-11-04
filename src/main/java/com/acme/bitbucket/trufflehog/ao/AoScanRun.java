package com.acme.bitbucket.trufflehog.ao;

import net.java.ao.Entity;
import net.java.ao.Preload;
import net.java.ao.schema.Indexed;
import net.java.ao.schema.NotNull;
import net.java.ao.schema.StringLength;
import net.java.ao.schema.Table;

@Preload
@Table("ScanRun")
public interface AoScanRun extends Entity {

    @Indexed
    int getRepositoryId();
    void setRepositoryId(int repositoryId);

    @Indexed
    String getProjectKey();
    void setProjectKey(String projectKey);

    String getRepositorySlug();
    void setRepositorySlug(String repositorySlug);

    @NotNull
    @StringLength(40)
    String getTriggerType();
    void setTriggerType(String triggerType);

    @StringLength(255)
    String getTriggeredBy();
    void setTriggeredBy(String triggeredBy);

    @Indexed
    long getStartedAt();
    void setStartedAt(long startedAt);

    long getFinishedAt();
    void setFinishedAt(long finishedAt);

    long getDurationMs();
    void setDurationMs(long durationMs);

    @NotNull
    @StringLength(40)
    String getStatus();
    void setStatus(String status);

    int getFindingsTotal();
    void setFindingsTotal(int findingsTotal);

    int getFindingsVerified();
    void setFindingsVerified(int findingsVerified);

    int getFindingsNew();
    void setFindingsNew(int findingsNew);

    @StringLength(StringLength.UNLIMITED)
    String getErrorMessage();
    void setErrorMessage(String errorMessage);

    @StringLength(80)
    String getRefName();
    void setRefName(String refName);

    @StringLength(80)
    String getFromCommit();
    void setFromCommit(String fromCommit);

    @StringLength(80)
    String getToCommit();
    void setToCommit(String toCommit);
}
