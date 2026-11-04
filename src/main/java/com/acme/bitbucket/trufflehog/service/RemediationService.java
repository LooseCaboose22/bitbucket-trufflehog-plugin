package com.acme.bitbucket.trufflehog.service;

import com.acme.bitbucket.trufflehog.ao.AoRemediationEvent;
import com.acme.bitbucket.trufflehog.ao.AoSecretFinding;
import com.acme.bitbucket.trufflehog.model.RemediationContext;
import com.acme.bitbucket.trufflehog.model.FindingStatus;
import com.acme.bitbucket.trufflehog.model.RemediationEventDto;
import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.bitbucket.auth.AuthenticationContext;
import com.atlassian.bitbucket.user.ApplicationUser;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import net.java.ao.DBParam;
import net.java.ao.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.ArrayList;
import java.util.List;

@Named
@Component
public class RemediationService {
    private static final Logger LOG = LoggerFactory.getLogger(RemediationService.class);

    private final ActiveObjects ao;
    private final AuthenticationContext authenticationContext;

    @Inject
    public RemediationService(
            @ComponentImport ActiveObjects ao,
            @ComponentImport AuthenticationContext authenticationContext) {
        this.ao = ao;
        this.authenticationContext = authenticationContext;
    }

    /**
     * Transition a finding to a new status and record an audit event.
     * Returns {@code true} on success, {@code false} when the finding was not
     * found.
     */
    public boolean transition(int findingId, FindingStatus newStatus, String note, RemediationContext context) {
        AoSecretFinding finding = ao.get(AoSecretFinding.class, findingId);
        if (finding == null) {
            return false;
        }
        String fromStatus = finding.getStatus();
        if (fromStatus != null && fromStatus.equals(newStatus.name())) {
            // No state change - still record the note if any.
            if (note != null && !note.isEmpty()) {
                finding.setRemediationNote(note);
                finding.save();
            }
            return true;
        }

        String actor = currentUsername();
        long now = System.currentTimeMillis();

        finding.setStatus(newStatus.name());
        finding.setRemediationNote(note == null ? "" : note);
        if (newStatus == FindingStatus.FIXED
                || newStatus == FindingStatus.FALSE_POSITIVE
                || newStatus == FindingStatus.ACCEPTED_RISK) {
            finding.setResolvedBy(actor);
            finding.setResolvedAt(now);
        } else if (newStatus == FindingStatus.OPEN) {
            finding.setResolvedBy("");
            finding.setResolvedAt(0L);
        }
        finding.save();

        AoRemediationEvent event = ao.create(AoRemediationEvent.class,
                new DBParam("FINDING_ID", findingId),
                new DBParam("FROM_STATUS", fromStatus == null ? "" : fromStatus),
                new DBParam("TO_STATUS", newStatus.name()),
                new DBParam("ACTOR", actor));
        event.setNote(note == null ? "" : note);
        event.setProjectKey(finding.getProjectKey());
        event.setRepositorySlug(finding.getRepositorySlug());
        event.setSourceIp(trimTo(context == null ? null : context.getSourceIp(), 64));
        event.setUserAgent(trimTo(context == null ? null : context.getUserAgent(), 1024));
        event.setOccurredAt(now);
        event.save();

        LOG.info("Finding {} transitioned {} -> {} by {}",
                findingId, fromStatus, newStatus.name(), actor);
        return true;
    }

    public List<RemediationEventDto> history(int findingId) {
        AoRemediationEvent[] rows = ao.find(AoRemediationEvent.class,
                Query.select().where("FINDING_ID = ?", findingId).order("OCCURRED_AT DESC"));
        List<RemediationEventDto> dtos = new ArrayList<>(rows.length);
        for (AoRemediationEvent row : rows) {
            RemediationEventDto dto = new RemediationEventDto();
            dto.setId(row.getID());
            dto.setFindingId(row.getFindingId());
            dto.setFromStatus(row.getFromStatus());
            dto.setToStatus(row.getToStatus());
            dto.setActor(row.getActor());
            dto.setNote(row.getNote());
            dto.setProjectKey(row.getProjectKey());
            dto.setRepositorySlug(row.getRepositorySlug());
            dto.setSourceIp(row.getSourceIp());
            dto.setUserAgent(row.getUserAgent());
            dto.setOccurredAt(row.getOccurredAt());
            dtos.add(dto);
        }
        return dtos;
    }

    private String trimTo(String value, int maxLen) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLen ? value : value.substring(0, maxLen);
    }

    private String currentUsername() {
        ApplicationUser user = authenticationContext == null ? null : authenticationContext.getCurrentUser();
        return user == null ? "system" : user.getName();
    }
}
