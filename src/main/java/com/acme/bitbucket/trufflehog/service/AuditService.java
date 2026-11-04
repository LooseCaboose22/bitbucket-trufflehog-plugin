package com.acme.bitbucket.trufflehog.service;

import com.acme.bitbucket.trufflehog.ao.AoAuditLogEntry;
import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import net.java.ao.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Arrays;
import java.util.List;

@Named
@Component
public class AuditService {
    private static final Logger LOG = LoggerFactory.getLogger(AuditService.class);

    private final ActiveObjects ao;

    @Inject
    public AuditService(@ComponentImport ActiveObjects ao) {
        this.ao = ao;
    }

    public void log(String action, String actor, String entityType, String entityId, String detail) {
        try {
            ao.executeInTransaction(() -> {
                AoAuditLogEntry entry = ao.create(AoAuditLogEntry.class);
                entry.setAction(action != null ? action : "");
                entry.setActor(actor != null ? actor : "system");
                entry.setEntityType(entityType != null ? entityType : "");
                entry.setEntityId(entityId != null ? entityId : "");
                entry.setDetail(detail != null ? detail.length() > 2000 ? detail.substring(0, 2000) : detail : "");
                entry.setOccurredAt(System.currentTimeMillis());
                entry.save();
                return null;
            });
        } catch (Exception ex) {
            LOG.warn("Failed to write audit log entry action={} actor={}: {}", action, actor, ex.getMessage());
        }
    }

    public List<AoAuditLogEntry> listRecent(int limit) {
        try {
            int cap = Math.min(Math.max(limit, 1), 500);
            AoAuditLogEntry[] rows = ao.find(AoAuditLogEntry.class,
                    Query.select().order("OCCURRED_AT DESC").limit(cap));
            return Arrays.asList(rows);
        } catch (Exception ex) {
            LOG.warn("Failed to read audit log: {}", ex.getMessage());
            return java.util.Collections.emptyList();
        }
    }
}
