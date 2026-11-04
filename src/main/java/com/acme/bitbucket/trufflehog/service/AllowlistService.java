package com.acme.bitbucket.trufflehog.service;

import com.acme.bitbucket.trufflehog.ao.AoSecretAllowlistEntry;
import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import net.java.ao.DBParam;
import net.java.ao.Query;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Arrays;
import java.util.List;

/**
 * Manages the per-secret allowlist stored in Active Objects.
 * Findings whose fingerprint appears here are silently skipped during upsert.
 */
@Named
@Component
public class AllowlistService {
    private final ActiveObjects ao;

    @Inject
    public AllowlistService(@ComponentImport ActiveObjects ao) {
        this.ao = ao;
    }

    public boolean isAllowlisted(String fingerprint) {
        return ao.count(AoSecretAllowlistEntry.class,
                Query.select().where("FINGERPRINT = ?", fingerprint)) > 0;
    }

    public AoSecretAllowlistEntry add(String fingerprint, String detectorName,
                                     String redacted, String reason, String addedBy) {
        if (isAllowlisted(fingerprint)) {
            AoSecretAllowlistEntry[] existing = ao.find(AoSecretAllowlistEntry.class,
                    Query.select().where("FINGERPRINT = ?", fingerprint));
            return existing.length > 0 ? existing[0] : null;
        }
        AoSecretAllowlistEntry entry = ao.create(AoSecretAllowlistEntry.class,
                new DBParam("FINGERPRINT", fingerprint));
        entry.setDetectorName(detectorName == null ? "" : detectorName);
        entry.setRedacted(redacted == null ? "" : redacted);
        entry.setReason(reason == null ? "" : reason);
        entry.setAddedBy(addedBy == null ? "" : addedBy);
        entry.setAddedAt(System.currentTimeMillis());
        entry.save();
        return entry;
    }

    public boolean remove(int id) {
        AoSecretAllowlistEntry entry = ao.get(AoSecretAllowlistEntry.class, id);
        if (entry == null) return false;
        ao.delete(entry);
        return true;
    }

    public List<AoSecretAllowlistEntry> listAll() {
        return Arrays.asList(ao.find(AoSecretAllowlistEntry.class,
                Query.select().order("ADDED_AT DESC")));
    }
}
