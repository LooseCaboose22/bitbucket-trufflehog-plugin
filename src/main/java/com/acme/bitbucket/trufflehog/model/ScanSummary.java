package com.acme.bitbucket.trufflehog.model;

import java.util.Map;
import java.util.TreeMap;

public class ScanSummary {
    private long open;
    private long fixed;
    private long falsePositive;
    private long acceptedRisk;
    private long verifiedOpen;
    private long total;
    private long affectedRepositories;
    private long affectedProjects;
    private Map<String, Long> byDetector = new TreeMap<>();
    private Map<String, Long> bySeverity = new TreeMap<>();
    private Map<String, Long> openFindingsByDay = new TreeMap<>();
    private Map<String, Long> newFindingsByDay = new TreeMap<>();
    private Map<String, Long> resolvedFindingsByDay = new TreeMap<>();
    private long lastScanAt;
    private long lastFindingAt;

    public long getOpen() { return open; }
    public void setOpen(long open) { this.open = open; }
    public long getFixed() { return fixed; }
    public void setFixed(long fixed) { this.fixed = fixed; }
    public long getFalsePositive() { return falsePositive; }
    public void setFalsePositive(long falsePositive) { this.falsePositive = falsePositive; }
    public long getAcceptedRisk() { return acceptedRisk; }
    public void setAcceptedRisk(long acceptedRisk) { this.acceptedRisk = acceptedRisk; }
    public long getVerifiedOpen() { return verifiedOpen; }
    public void setVerifiedOpen(long verifiedOpen) { this.verifiedOpen = verifiedOpen; }
    public long getTotal() { return total; }
    public void setTotal(long total) { this.total = total; }
    public long getAffectedRepositories() { return affectedRepositories; }
    public void setAffectedRepositories(long affectedRepositories) { this.affectedRepositories = affectedRepositories; }
    public long getAffectedProjects() { return affectedProjects; }
    public void setAffectedProjects(long affectedProjects) { this.affectedProjects = affectedProjects; }
    public Map<String, Long> getByDetector() { return byDetector; }
    public void setByDetector(Map<String, Long> byDetector) { this.byDetector = byDetector; }
    public Map<String, Long> getBySeverity() { return bySeverity; }
    public void setBySeverity(Map<String, Long> bySeverity) { this.bySeverity = bySeverity; }
    public Map<String, Long> getOpenFindingsByDay() { return openFindingsByDay; }
    public void setOpenFindingsByDay(Map<String, Long> openFindingsByDay) { this.openFindingsByDay = openFindingsByDay; }
    public Map<String, Long> getNewFindingsByDay() { return newFindingsByDay; }
    public void setNewFindingsByDay(Map<String, Long> newFindingsByDay) { this.newFindingsByDay = newFindingsByDay; }
    public Map<String, Long> getResolvedFindingsByDay() { return resolvedFindingsByDay; }
    public void setResolvedFindingsByDay(Map<String, Long> resolvedFindingsByDay) { this.resolvedFindingsByDay = resolvedFindingsByDay; }
    public long getLastScanAt() { return lastScanAt; }
    public void setLastScanAt(long lastScanAt) { this.lastScanAt = lastScanAt; }
    public long getLastFindingAt() { return lastFindingAt; }
    public void setLastFindingAt(long lastFindingAt) { this.lastFindingAt = lastFindingAt; }
}
