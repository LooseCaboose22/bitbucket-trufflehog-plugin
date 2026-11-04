package com.acme.bitbucket.trufflehog.ao;

import net.java.ao.Entity;
import net.java.ao.Preload;
import net.java.ao.schema.Indexed;
import net.java.ao.schema.NotNull;
import net.java.ao.schema.StringLength;
import net.java.ao.schema.Table;

/**
 * Per-scope (system / project / repository) overrides.
 * <p>
 * Scope is one of SYSTEM, PROJECT, REPOSITORY. For SYSTEM, scopeKey is empty.
 * For PROJECT, scopeKey is the project key. For REPOSITORY, scopeKey is
 * "projectKey/repositorySlug".
 */
@Preload
@Table("ScanConfig")
public interface AoScanConfig extends Entity {

    @Indexed
    @NotNull
    @StringLength(40)
    String getScope();
    void setScope(String scope);

    @Indexed
    @NotNull
    @StringLength(255)
    String getScopeKey();
    void setScopeKey(String scopeKey);

    boolean isEnabled();
    void setEnabled(boolean enabled);

    boolean isOnlyVerified();
    void setOnlyVerified(boolean onlyVerified);

    boolean isBlockPushOnFinding();
    void setBlockPushOnFinding(boolean blockPushOnFinding);

    @StringLength(StringLength.UNLIMITED)
    String getIncludePatterns();
    void setIncludePatterns(String includePatterns);

    @StringLength(StringLength.UNLIMITED)
    String getExcludePatterns();
    void setExcludePatterns(String excludePatterns);

    @StringLength(StringLength.UNLIMITED)
    String getAllowlistFingerprints();
    void setAllowlistFingerprints(String allowlistFingerprints);
}
