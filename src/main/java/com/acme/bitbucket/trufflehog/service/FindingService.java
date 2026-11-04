package com.acme.bitbucket.trufflehog.service;

import com.acme.bitbucket.trufflehog.ao.AoSecretFinding;
import com.acme.bitbucket.trufflehog.model.FindingStatus;
import com.acme.bitbucket.trufflehog.model.SecretFinding;
import com.acme.bitbucket.trufflehog.model.Severity;
import com.acme.bitbucket.trufflehog.model.TruffleHogRawFinding;
import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import net.java.ao.DBParam;
import net.java.ao.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AO-backed CRUD on findings. Upserts on (repositoryId, fingerprint).
 */
@Named
@Component
public class FindingService {
    private static final Logger LOG = LoggerFactory.getLogger(FindingService.class);

    private final ActiveObjects ao;
    private final FingerprintService fingerprintService;
    private static final Map<String, String> SORT_COLUMNS = sortColumns();

    private final AllowlistService allowlistService;

    @Inject
    public FindingService(@ComponentImport ActiveObjects ao, FingerprintService fingerprintService,
                          AllowlistService allowlistService) {
        this.ao = ao;
        this.fingerprintService = fingerprintService;
        this.allowlistService = allowlistService;
    }

    /**
     * Upsert a raw TruffleHog finding into AO. Returns {@code true} when a new
     * record was created, {@code false} when an existing one was updated.
     * Returns {@code false} silently if the finding's fingerprint is on the allowlist.
     */
    public boolean upsert(Repository repository, TruffleHogRawFinding raw) {
        String fingerprint = fingerprintService.fingerprintFor((int) repository.getId(), raw);
        if (allowlistService.isAllowlisted(fingerprint)) {
            LOG.debug("Skipping allowlisted finding {} in {}/{}", fingerprint,
                    repository.getProject().getKey(), repository.getSlug());
            return false;
        }
        AoSecretFinding[] existing = ao.find(AoSecretFinding.class,
                Query.select().where("FINGERPRINT = ? AND REPOSITORY_ID = ?", fingerprint, (int) repository.getId()));

        long now = System.currentTimeMillis();
        if (existing.length > 0) {
            AoSecretFinding f = existing[0];
            f.setLastSeenAt(now);
            f.setOccurrenceCount(f.getOccurrenceCount() + 1);
            f.setVerified(raw.isVerified() || f.isVerified());
            // Keep existing status / remediation - re-detection does not re-open.
            if (raw.getCommit() != null && !raw.getCommit().isEmpty()) {
                f.setCommit(raw.getCommit());
            }
            if (raw.getBranch() != null && !raw.getBranch().isEmpty()) {
                f.setBranch(raw.getBranch());
            }
            f.save();
            return false;
        }

        AoSecretFinding f = ao.create(AoSecretFinding.class,
                new DBParam("FINGERPRINT", fingerprint),
                new DBParam("REPOSITORY_ID", (int) repository.getId()),
                new DBParam("STATUS", FindingStatus.OPEN.name()));
        f.setProjectKey(repository.getProject().getKey());
        f.setRepositorySlug(repository.getSlug());
        f.setDetectorType(nullSafe(raw.getDetectorType()));
        f.setDetectorName(nullSafe(raw.getDetectorName()));
        f.setVerified(raw.isVerified());
        f.setRedacted(nullSafe(raw.getRedacted()));
        f.setFilePath(nullSafe(raw.getFilePath()));
        f.setLineNumber(raw.getLineNumber());
        f.setBranch(nullSafe(raw.getBranch()));
        f.setCommit(nullSafe(raw.getCommit()));
        f.setCommitAuthor(nullSafe(raw.getCommitAuthor()));
        f.setCommitTimestamp(raw.getCommitTimestamp());
        f.setSeverity(Severity.fromVerified(raw.isVerified()).name());
        f.setDiscoveredAt(now);
        f.setLastSeenAt(now);
        f.setOccurrenceCount(1);
        f.save();
        return true;
    }

    public SecretFinding findById(int id) {
        AoSecretFinding f = ao.get(AoSecretFinding.class, id);
        return f == null ? null : toDto(f);
    }

    public AoSecretFinding rawById(int id) {
        return ao.get(AoSecretFinding.class, id);
    }

    public List<SecretFinding> list(FindingFilter filter) {
        Query q = Query.select().order(resolveOrderBy(filter.sortBy, filter.sortDirection));
        StringBuilder where = new StringBuilder();
        List<Object> args = new ArrayList<>();
        if (filter.projectKey != null && !filter.projectKey.isEmpty()) {
            append(where, "PROJECT_KEY = ?");
            args.add(filter.projectKey);
        }
        if (filter.repositorySlug != null && !filter.repositorySlug.isEmpty()) {
            append(where, "REPOSITORY_SLUG = ?");
            args.add(filter.repositorySlug);
        }
        if (filter.repositoryId > 0) {
            append(where, "REPOSITORY_ID = ?");
            args.add(filter.repositoryId);
        }
        if (filter.status != null) {
            append(where, "STATUS = ?");
            args.add(filter.status.name());
        }
        if (filter.onlyVerified) {
            append(where, "VERIFIED = ?");
            args.add(true);
        }
        if (filter.detectorType != null && !filter.detectorType.isEmpty()) {
            append(where, "DETECTOR_TYPE = ?");
            args.add(filter.detectorType);
        }
        if (where.length() > 0) {
            q = q.where(where.toString(), args.toArray());
        }
        if (filter.limit > 0) {
            q = q.limit(filter.limit);
        }
        if (filter.offset > 0) {
            q = q.offset(filter.offset);
        }

        AoSecretFinding[] rows = ao.find(AoSecretFinding.class, q);
        List<SecretFinding> dtos = new ArrayList<>(rows.length);
        for (AoSecretFinding row : rows) {
            dtos.add(toDto(row));
        }
        return dtos;
    }

    public long count(FindingFilter filter) {
        StringBuilder where = new StringBuilder();
        List<Object> args = new ArrayList<>();
        if (filter.projectKey != null && !filter.projectKey.isEmpty()) {
            append(where, "PROJECT_KEY = ?");
            args.add(filter.projectKey);
        }
        if (filter.repositorySlug != null && !filter.repositorySlug.isEmpty()) {
            append(where, "REPOSITORY_SLUG = ?");
            args.add(filter.repositorySlug);
        }
        if (filter.repositoryId > 0) {
            append(where, "REPOSITORY_ID = ?");
            args.add(filter.repositoryId);
        }
        if (filter.status != null) {
            append(where, "STATUS = ?");
            args.add(filter.status.name());
        }
        if (filter.onlyVerified) {
            append(where, "VERIFIED = ?");
            args.add(true);
        }
        if (filter.detectorType != null && !filter.detectorType.isEmpty()) {
            append(where, "DETECTOR_TYPE = ?");
            args.add(filter.detectorType);
        }
        Query q = where.length() > 0
                ? Query.select().where(where.toString(), args.toArray())
                : Query.select();
        return ao.count(AoSecretFinding.class, q);
    }

    public void deleteForRepository(int repositoryId) {
        AoSecretFinding[] rows = ao.find(AoSecretFinding.class,
                Query.select().where("REPOSITORY_ID = ?", repositoryId));
        for (AoSecretFinding row : rows) {
            ao.delete(row);
        }
    }

    public AoSecretFinding[] all() {
        return ao.find(AoSecretFinding.class);
    }

    public SecretFinding toDto(AoSecretFinding row) {
        SecretFinding dto = new SecretFinding();
        dto.setId(row.getID());
        dto.setFingerprint(row.getFingerprint());
        dto.setRepositoryId(row.getRepositoryId());
        dto.setProjectKey(row.getProjectKey());
        dto.setRepositorySlug(row.getRepositorySlug());
        dto.setDetectorType(row.getDetectorType());
        dto.setDetectorName(row.getDetectorName());
        dto.setVerified(row.isVerified());
        dto.setRedacted(row.getRedacted());
        dto.setFilePath(row.getFilePath());
        dto.setLineNumber(row.getLineNumber());
        dto.setBranch(row.getBranch());
        dto.setCommit(row.getCommit());
        dto.setCommitAuthor(row.getCommitAuthor());
        dto.setCommitTimestamp(row.getCommitTimestamp());
        dto.setSeverity(row.getSeverity());
        dto.setStatus(row.getStatus());
        dto.setRemediationNote(row.getRemediationNote());
        dto.setResolvedBy(row.getResolvedBy());
        dto.setResolvedAt(row.getResolvedAt());
        dto.setDiscoveredAt(row.getDiscoveredAt());
        dto.setLastSeenAt(row.getLastSeenAt());
        dto.setOccurrenceCount(row.getOccurrenceCount());
        return dto;
    }

    private void append(StringBuilder builder, String clause) {
        if (builder.length() > 0) {
            builder.append(" AND ");
        }
        builder.append(clause);
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private String resolveOrderBy(String sortBy, String sortDirection) {
        String column = SORT_COLUMNS.getOrDefault(sortBy == null ? "" : sortBy.trim().toLowerCase(), "LAST_SEEN_AT");
        boolean asc = "asc".equalsIgnoreCase(sortDirection);
        return column + (asc ? " ASC" : " DESC");
    }

    private static Map<String, String> sortColumns() {
        Map<String, String> map = new HashMap<>();
        map.put("lastseenat", "LAST_SEEN_AT");
        map.put("discoveredat", "DISCOVERED_AT");
        map.put("severity", "SEVERITY");
        map.put("status", "STATUS");
        map.put("detectortype", "DETECTOR_TYPE");
        map.put("projectkey", "PROJECT_KEY");
        map.put("repositoryslug", "REPOSITORY_SLUG");
        return map;
    }

    /**
     * Mutable filter used by query methods.
     */
    public static class FindingFilter {
        public String projectKey;
        public String repositorySlug;
        public int repositoryId;
        public FindingStatus status;
        public boolean onlyVerified;
        public String detectorType;
        public String sortBy;
        public String sortDirection;
        public int limit;
        public int offset;
    }
}
