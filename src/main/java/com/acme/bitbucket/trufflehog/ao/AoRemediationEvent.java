package com.acme.bitbucket.trufflehog.ao;

import net.java.ao.Entity;
import net.java.ao.Preload;
import net.java.ao.schema.Indexed;
import net.java.ao.schema.NotNull;
import net.java.ao.schema.StringLength;
import net.java.ao.schema.Table;

@Preload
@Table("Remediation")
public interface AoRemediationEvent extends Entity {

    @Indexed
    @NotNull
    int getFindingId();
    void setFindingId(int findingId);

    @NotNull
    @StringLength(40)
    String getFromStatus();
    void setFromStatus(String fromStatus);

    @NotNull
    @StringLength(40)
    String getToStatus();
    void setToStatus(String toStatus);

    @NotNull
    @StringLength(255)
    String getActor();
    void setActor(String actor);

    @StringLength(StringLength.UNLIMITED)
    String getNote();
    void setNote(String note);

    @Indexed
    @StringLength(80)
    String getProjectKey();
    void setProjectKey(String projectKey);

    @Indexed
    @StringLength(255)
    String getRepositorySlug();
    void setRepositorySlug(String repositorySlug);

    @StringLength(64)
    String getSourceIp();
    void setSourceIp(String sourceIp);

    @StringLength(450)
    String getUserAgent();
    void setUserAgent(String userAgent);

    @Indexed
    long getOccurredAt();
    void setOccurredAt(long occurredAt);
}
