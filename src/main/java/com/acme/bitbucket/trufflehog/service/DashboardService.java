package com.acme.bitbucket.trufflehog.service;

import com.acme.bitbucket.trufflehog.ao.AoScanRun;
import com.acme.bitbucket.trufflehog.ao.AoSecretFinding;
import com.acme.bitbucket.trufflehog.model.FindingStatus;
import com.acme.bitbucket.trufflehog.model.ScanRunDto;
import com.acme.bitbucket.trufflehog.model.ScanSummary;
import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import net.java.ao.Query;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import javax.inject.Named;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

/**
 * Aggregations for the system / project / repository dashboards.
 */
@Named
@Component
public class DashboardService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            .withZone(ZoneId.systemDefault());

    private final ActiveObjects ao;

    @Inject
    public DashboardService(@ComponentImport ActiveObjects ao) {
        this.ao = ao;
    }

    public ScanSummary summarize(String projectKey, String repositorySlug) {
        AoSecretFinding[] rows = queryFindings(projectKey, repositorySlug);
        ScanSummary summary = new ScanSummary();
        summary.setTotal(rows.length);

        Set<Integer> repositoryIds = new HashSet<>();
        Set<String> projectKeys = new HashSet<>();
        TreeMap<String, Long> openByDay = new TreeMap<>();
        TreeMap<String, Long> newByDay = new TreeMap<>();
        TreeMap<String, Long> resolvedByDay = new TreeMap<>();

        for (AoSecretFinding f : rows) {
            repositoryIds.add(f.getRepositoryId());
            if (f.getProjectKey() != null) {
                projectKeys.add(f.getProjectKey());
            }
            String status = f.getStatus();
            if (status == null) {
                status = FindingStatus.OPEN.name();
            }
            switch (FindingStatus.fromString(status)) {
                case OPEN:
                    summary.setOpen(summary.getOpen() + 1);
                    if (f.isVerified()) {
                        summary.setVerifiedOpen(summary.getVerifiedOpen() + 1);
                    }
                    break;
                case FIXED:
                    summary.setFixed(summary.getFixed() + 1);
                    if (f.getResolvedAt() > 0) {
                        String day = DAY.format(Instant.ofEpochMilli(f.getResolvedAt()));
                        resolvedByDay.merge(day, 1L, Long::sum);
                    }
                    break;
                case FALSE_POSITIVE:
                    summary.setFalsePositive(summary.getFalsePositive() + 1);
                    if (f.getResolvedAt() > 0) {
                        String day = DAY.format(Instant.ofEpochMilli(f.getResolvedAt()));
                        resolvedByDay.merge(day, 1L, Long::sum);
                    }
                    break;
                case ACCEPTED_RISK:
                    summary.setAcceptedRisk(summary.getAcceptedRisk() + 1);
                    if (f.getResolvedAt() > 0) {
                        String day = DAY.format(Instant.ofEpochMilli(f.getResolvedAt()));
                        resolvedByDay.merge(day, 1L, Long::sum);
                    }
                    break;
            }

            String detector = f.getDetectorType() == null ? "Unknown" : f.getDetectorType();
            summary.getByDetector().merge(detector, 1L, Long::sum);

            String severity = f.getSeverity() == null ? "MEDIUM" : f.getSeverity();
            summary.getBySeverity().merge(severity, 1L, Long::sum);

            if (FindingStatus.fromString(status) == FindingStatus.OPEN && f.getDiscoveredAt() > 0) {
                String day = DAY.format(Instant.ofEpochMilli(f.getDiscoveredAt()));
                openByDay.merge(day, 1L, Long::sum);
            }
            if (f.getDiscoveredAt() > 0) {
                String day = DAY.format(Instant.ofEpochMilli(f.getDiscoveredAt()));
                newByDay.merge(day, 1L, Long::sum);
            }
            if (f.getLastSeenAt() > summary.getLastFindingAt()) {
                summary.setLastFindingAt(f.getLastSeenAt());
            }
        }

        summary.setAffectedRepositories(repositoryIds.size());
        summary.setAffectedProjects(projectKeys.size());
        summary.setOpenFindingsByDay(openByDay);
        summary.setNewFindingsByDay(newByDay);
        summary.setResolvedFindingsByDay(resolvedByDay);

        // Last scan timestamp
        AoScanRun[] runs = ao.find(AoScanRun.class,
                Query.select().order("STARTED_AT DESC").limit(1));
        if (runs.length > 0) {
            summary.setLastScanAt(runs[0].getStartedAt());
        }
        return summary;
    }

    public List<ScanRunDto> recentScanRuns(String projectKey, String repositorySlug, int limit) {
        StringBuilder where = new StringBuilder();
        List<Object> args = new ArrayList<>();
        if (projectKey != null && !projectKey.isEmpty()) {
            where.append("PROJECT_KEY = ?");
            args.add(projectKey);
        }
        if (repositorySlug != null && !repositorySlug.isEmpty()) {
            if (where.length() > 0) {
                where.append(" AND ");
            }
            where.append("REPOSITORY_SLUG = ?");
            args.add(repositorySlug);
        }
        Query q = Query.select().order("STARTED_AT DESC").limit(Math.max(1, limit));
        if (where.length() > 0) {
            q = q.where(where.toString(), args.toArray());
        }
        AoScanRun[] runs = ao.find(AoScanRun.class, q);
        List<ScanRunDto> dtos = new ArrayList<>(runs.length);
        for (AoScanRun run : runs) {
            ScanRunDto dto = new ScanRunDto();
            dto.setId(run.getID());
            dto.setRepositoryId(run.getRepositoryId());
            dto.setProjectKey(run.getProjectKey());
            dto.setRepositorySlug(run.getRepositorySlug());
            dto.setTriggerType(run.getTriggerType());
            dto.setTriggeredBy(run.getTriggeredBy());
            dto.setStartedAt(run.getStartedAt());
            dto.setFinishedAt(run.getFinishedAt());
            dto.setDurationMs(run.getDurationMs());
            dto.setStatus(run.getStatus());
            dto.setFindingsTotal(run.getFindingsTotal());
            dto.setFindingsVerified(run.getFindingsVerified());
            dto.setFindingsNew(run.getFindingsNew());
            dto.setErrorMessage(run.getErrorMessage());
            dto.setRefName(run.getRefName());
            dto.setFromCommit(run.getFromCommit());
            dto.setToCommit(run.getToCommit());
            dtos.add(dto);
        }
        return dtos;
    }

    public List<RepositoryRollup> topRepositories(String projectKey, int limit) {
        AoSecretFinding[] rows = queryFindings(projectKey, null);
        java.util.Map<Integer, RepositoryRollup> byRepo = new java.util.HashMap<>();
        for (AoSecretFinding f : rows) {
            if (!FindingStatus.OPEN.name().equals(f.getStatus())) {
                continue;
            }
            RepositoryRollup r = byRepo.computeIfAbsent(f.getRepositoryId(), id -> {
                RepositoryRollup rr = new RepositoryRollup();
                rr.repositoryId = id;
                rr.projectKey = f.getProjectKey();
                rr.repositorySlug = f.getRepositorySlug();
                return rr;
            });
            r.openCount++;
            if (f.isVerified()) {
                r.verifiedOpen++;
            }
            if (f.getLastSeenAt() > r.lastFindingAt) {
                r.lastFindingAt = f.getLastSeenAt();
            }
        }
        List<RepositoryRollup> out = new ArrayList<>(byRepo.values());
        out.sort((a, b) -> Integer.compare(b.openCount, a.openCount));
        if (out.size() > limit) {
            return out.subList(0, limit);
        }
        return out;
    }

    public List<ProjectRollup> projectRollups() {
        AoSecretFinding[] rows = queryFindings(null, null);
        java.util.Map<String, ProjectRollup> byProj = new java.util.HashMap<>();
        for (AoSecretFinding f : rows) {
            String key = f.getProjectKey() == null ? "" : f.getProjectKey();
            ProjectRollup p = byProj.computeIfAbsent(key, k -> {
                ProjectRollup pr = new ProjectRollup();
                pr.projectKey = k;
                return pr;
            });
            String status = f.getStatus() == null ? FindingStatus.OPEN.name() : f.getStatus();
            switch (FindingStatus.fromString(status)) {
                case OPEN:
                    p.open++;
                    if (f.isVerified()) {
                        p.verifiedOpen++;
                    }
                    break;
                case FIXED:
                    p.fixed++;
                    break;
                case FALSE_POSITIVE:
                    p.falsePositive++;
                    break;
                case ACCEPTED_RISK:
                    p.acceptedRisk++;
                    break;
            }
            if (f.getLastSeenAt() > p.lastFindingAt) {
                p.lastFindingAt = f.getLastSeenAt();
            }
        }
        List<ProjectRollup> out = new ArrayList<>(byProj.values());
        out.sort((a, b) -> Integer.compare(b.open, a.open));
        return out;
    }

    private AoSecretFinding[] queryFindings(String projectKey, String repositorySlug) {
        if (projectKey == null && repositorySlug == null) {
            return ao.find(AoSecretFinding.class);
        }
        StringBuilder where = new StringBuilder();
        List<Object> args = new ArrayList<>();
        if (projectKey != null && !projectKey.isEmpty()) {
            where.append("PROJECT_KEY = ?");
            args.add(projectKey);
        }
        if (repositorySlug != null && !repositorySlug.isEmpty()) {
            if (where.length() > 0) {
                where.append(" AND ");
            }
            where.append("REPOSITORY_SLUG = ?");
            args.add(repositorySlug);
        }
        return ao.find(AoSecretFinding.class, Query.select().where(where.toString(), args.toArray()));
    }

    public static class RepositoryRollup {
        public int repositoryId;
        public String projectKey;
        public String repositorySlug;
        public int openCount;
        public int verifiedOpen;
        public long lastFindingAt;
    }

    public static class ProjectRollup {
        public String projectKey;
        public int open;
        public int verifiedOpen;
        public int fixed;
        public int falsePositive;
        public int acceptedRisk;
        public long lastFindingAt;
    }
}
