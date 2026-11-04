package com.acme.bitbucket.trufflehog.service;

import com.acme.bitbucket.trufflehog.ao.AoSecretFinding;
import com.acme.bitbucket.trufflehog.model.FindingStatus;
import com.acme.bitbucket.trufflehog.model.SecretFinding;
import com.atlassian.activeobjects.external.ActiveObjects;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class FindingServiceTest {

    private FindingService service;
    private ActiveObjects ao;
    private FingerprintService fingerprintService;

    private AllowlistService allowlistService;

    @Before
    public void setUp() {
        ao = mock(ActiveObjects.class);
        fingerprintService = new FingerprintService();
        allowlistService = mock(AllowlistService.class);
        service = new FindingService(ao, fingerprintService, allowlistService);
    }

    // ---- toDto mapping ----

    @Test
    public void toDtoMapsAllFields() {
        AoSecretFinding row = mockFinding();
        SecretFinding dto = service.toDto(row);

        assertEquals(99,             dto.getId());
        assertEquals("fp-abc",       dto.getFingerprint());
        assertEquals(7,              dto.getRepositoryId());
        assertEquals("PROJ",         dto.getProjectKey());
        assertEquals("my-repo",      dto.getRepositorySlug());
        assertEquals("AWS",          dto.getDetectorType());
        assertEquals("AWSKeyDetector", dto.getDetectorName());
        assertTrue(dto.isVerified());
        assertEquals("AKIA***",      dto.getRedacted());
        assertEquals("src/cfg.ts",   dto.getFilePath());
        assertEquals(42,             dto.getLineNumber());
        assertEquals("main",         dto.getBranch());
        assertEquals("deadbeef",     dto.getCommit());
        assertEquals("author@example.com", dto.getCommitAuthor());
        assertEquals(1700000000000L, dto.getCommitTimestamp());
        assertEquals("CRITICAL",     dto.getSeverity());
        assertEquals("OPEN",         dto.getStatus());
        assertEquals("rotate the key", dto.getRemediationNote());
        assertEquals("admin",        dto.getResolvedBy());
        assertEquals(1700001000000L, dto.getResolvedAt());
        assertEquals(1699990000000L, dto.getDiscoveredAt());
        assertEquals(1700000000000L, dto.getLastSeenAt());
        assertEquals(3,              dto.getOccurrenceCount());
    }

    @Test
    public void toDtoHandlesNullStrings() {
        AoSecretFinding row = mock(AoSecretFinding.class);
        when(row.getID()).thenReturn(1);
        when(row.getFingerprint()).thenReturn(null);
        when(row.getProjectKey()).thenReturn(null);
        when(row.getRepositorySlug()).thenReturn(null);
        when(row.getStatus()).thenReturn(null);

        SecretFinding dto = service.toDto(row);

        assertNull(dto.getFingerprint());
        assertNull(dto.getProjectKey());
    }

    @Test
    public void toDtoDefaultsNumericZero() {
        AoSecretFinding row = mock(AoSecretFinding.class);
        when(row.getID()).thenReturn(0);
        when(row.getLineNumber()).thenReturn(0);
        when(row.getOccurrenceCount()).thenReturn(0);

        SecretFinding dto = service.toDto(row);

        assertEquals(0, dto.getLineNumber());
        assertEquals(0, dto.getOccurrenceCount());
    }

    // ---- resolveOrderBy ----

    @Test
    public void sortByLastSeenAtDefault() throws Exception {
        assertEquals("LAST_SEEN_AT DESC", resolveOrderBy(null, null));
        assertEquals("LAST_SEEN_AT DESC", resolveOrderBy("unknown_column", "DESC"));
        assertEquals("LAST_SEEN_AT ASC",  resolveOrderBy("lastseenat", "ASC"));
    }

    @Test
    public void sortByKnownColumns() throws Exception {
        assertEquals("DISCOVERED_AT DESC",  resolveOrderBy("discoveredat", "DESC"));
        assertEquals("SEVERITY ASC",        resolveOrderBy("severity", "asc"));
        assertEquals("STATUS DESC",         resolveOrderBy("status", "desc"));
        assertEquals("DETECTOR_TYPE ASC",   resolveOrderBy("detectortype", "ASC"));
        assertEquals("PROJECT_KEY DESC",    resolveOrderBy("projectkey", "DESC"));
        assertEquals("REPOSITORY_SLUG ASC", resolveOrderBy("repositoryslug", "asc"));
    }

    @Test
    public void sortDirectionCaseInsensitive() throws Exception {
        assertEquals("SEVERITY ASC",  resolveOrderBy("severity", "ASC"));
        assertEquals("SEVERITY ASC",  resolveOrderBy("severity", "asc"));
        assertEquals("SEVERITY DESC", resolveOrderBy("severity", "DESC"));
        assertEquals("SEVERITY DESC", resolveOrderBy("severity", "desc"));
    }

    @Test
    public void sortByIsCaseInsensitive() throws Exception {
        assertEquals("LAST_SEEN_AT DESC", resolveOrderBy("lastSeenAt",  "DESC"));
        assertEquals("LAST_SEEN_AT DESC", resolveOrderBy("LASTSEENAT",  "DESC"));
        assertEquals("SEVERITY DESC",     resolveOrderBy("SEVERITY",    "DESC"));
        assertEquals("DETECTOR_TYPE DESC", resolveOrderBy("DetectorType", "DESC"));
    }

    // ---- FindingFilter construction ----

    @Test
    public void filterDefaultValues() {
        FindingService.FindingFilter filter = new FindingService.FindingFilter();
        assertNull(filter.projectKey);
        assertNull(filter.repositorySlug);
        assertEquals(0, filter.repositoryId);
        assertNull(filter.status);
        assertFalse(filter.onlyVerified);
        assertNull(filter.detectorType);
        assertEquals(0, filter.limit);
        assertEquals(0, filter.offset);
    }

    @Test
    public void filterFieldsArePubliclyMutable() {
        FindingService.FindingFilter filter = new FindingService.FindingFilter();
        filter.projectKey     = "MYPROJ";
        filter.repositorySlug = "my-repo";
        filter.repositoryId   = 42;
        filter.status         = FindingStatus.OPEN;
        filter.onlyVerified   = true;
        filter.detectorType   = "AWS";
        filter.sortBy         = "severity";
        filter.sortDirection  = "DESC";
        filter.limit          = 50;
        filter.offset         = 100;

        assertEquals("MYPROJ",          filter.projectKey);
        assertEquals("my-repo",         filter.repositorySlug);
        assertEquals(42,                filter.repositoryId);
        assertEquals(FindingStatus.OPEN, filter.status);
        assertTrue(filter.onlyVerified);
        assertEquals("AWS",             filter.detectorType);
        assertEquals(50,                filter.limit);
        assertEquals(100,               filter.offset);
    }

    // ---- helpers ----

    private AoSecretFinding mockFinding() {
        AoSecretFinding row = mock(AoSecretFinding.class);
        when(row.getID()).thenReturn(99);
        when(row.getFingerprint()).thenReturn("fp-abc");
        when(row.getRepositoryId()).thenReturn(7);
        when(row.getProjectKey()).thenReturn("PROJ");
        when(row.getRepositorySlug()).thenReturn("my-repo");
        when(row.getDetectorType()).thenReturn("AWS");
        when(row.getDetectorName()).thenReturn("AWSKeyDetector");
        when(row.isVerified()).thenReturn(true);
        when(row.getRedacted()).thenReturn("AKIA***");
        when(row.getFilePath()).thenReturn("src/cfg.ts");
        when(row.getLineNumber()).thenReturn(42);
        when(row.getBranch()).thenReturn("main");
        when(row.getCommit()).thenReturn("deadbeef");
        when(row.getCommitAuthor()).thenReturn("author@example.com");
        when(row.getCommitTimestamp()).thenReturn(1700000000000L);
        when(row.getSeverity()).thenReturn("CRITICAL");
        when(row.getStatus()).thenReturn("OPEN");
        when(row.getRemediationNote()).thenReturn("rotate the key");
        when(row.getResolvedBy()).thenReturn("admin");
        when(row.getResolvedAt()).thenReturn(1700001000000L);
        when(row.getDiscoveredAt()).thenReturn(1699990000000L);
        when(row.getLastSeenAt()).thenReturn(1700000000000L);
        when(row.getOccurrenceCount()).thenReturn(3);
        return row;
    }

    private String resolveOrderBy(String sortBy, String sortDirection) throws Exception {
        Method m = FindingService.class.getDeclaredMethod("resolveOrderBy", String.class, String.class);
        m.setAccessible(true);
        return (String) m.invoke(service, sortBy, sortDirection);
    }
}
