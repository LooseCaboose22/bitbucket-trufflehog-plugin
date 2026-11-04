package com.acme.bitbucket.trufflehog.ao;

import net.java.ao.Entity;
import net.java.ao.Preload;
import net.java.ao.schema.NotNull;
import net.java.ao.schema.Table;

@Preload
@Table("AUDIT_LOG")
public interface AoAuditLogEntry extends Entity {

    @NotNull
    String getAction();
    void setAction(String action);

    String getActor();
    void setActor(String actor);

    String getEntityType();
    void setEntityType(String entityType);

    String getEntityId();
    void setEntityId(String entityId);

    String getDetail();
    void setDetail(String detail);

    long getOccurredAt();
    void setOccurredAt(long occurredAt);
}
