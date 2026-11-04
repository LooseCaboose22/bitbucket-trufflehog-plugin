package com.acme.bitbucket.trufflehog.ao;

import net.java.ao.Entity;
import net.java.ao.Preload;
import net.java.ao.schema.NotNull;
import net.java.ao.schema.Table;
import net.java.ao.schema.Unique;

/**
 * An entry on the per-secret allowlist. A finding whose fingerprint matches any
 * row here is silently skipped during upsert — it will never appear in dashboards.
 */
@Preload
@Table("SECRET_ALLOWLIST")
public interface AoSecretAllowlistEntry extends Entity {

    @NotNull
    @Unique
    String getFingerprint();
    void setFingerprint(String fingerprint);

    String getDetectorName();
    void setDetectorName(String detectorName);

    /** Redacted secret value shown to admins for identification. */
    String getRedacted();
    void setRedacted(String redacted);

    String getReason();
    void setReason(String reason);

    String getAddedBy();
    void setAddedBy(String addedBy);

    long getAddedAt();
    void setAddedAt(long addedAt);
}
