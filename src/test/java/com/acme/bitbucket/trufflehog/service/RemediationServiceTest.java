package com.acme.bitbucket.trufflehog.service;

import com.acme.bitbucket.trufflehog.ao.AoRemediationEvent;
import com.acme.bitbucket.trufflehog.ao.AoSecretFinding;
import com.acme.bitbucket.trufflehog.model.FindingStatus;
import com.acme.bitbucket.trufflehog.model.RemediationContext;
import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.bitbucket.auth.AuthenticationContext;
import com.atlassian.bitbucket.user.ApplicationUser;
import net.java.ao.DBParam;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Method;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class RemediationServiceTest {

    private RemediationService service;
    private ActiveObjects ao;
    private AuthenticationContext authCtx;

    @Before
    public void setUp() {
        ao = mock(ActiveObjects.class);
        authCtx = mock(AuthenticationContext.class);
        service = new RemediationService(ao, authCtx);
    }

    // ---- transition ----

    @Test
    public void transitionReturnsFalseWhenFindingNotFound() {
        when(ao.get(AoSecretFinding.class, 999)).thenReturn(null);
        assertFalse(service.transition(999, FindingStatus.FIXED, "note", null));
    }

    @Test
    public void transitionUpdatesStatusAndSavesEvent() {
        AoSecretFinding finding = mockFinding(1, "OPEN", "PROJ", "my-repo");
        AoRemediationEvent event = mock(AoRemediationEvent.class);
        ApplicationUser user = mock(ApplicationUser.class);
        when(user.getName()).thenReturn("alice");
        when(authCtx.getCurrentUser()).thenReturn(user);
        when(ao.get(AoSecretFinding.class, 1)).thenReturn(finding);
        when(ao.create(eq(AoRemediationEvent.class), any(DBParam.class), any(DBParam.class),
                any(DBParam.class), any(DBParam.class))).thenReturn(event);

        boolean result = service.transition(1, FindingStatus.FIXED, "credential rotated", null);

        assertTrue(result);
        verify(finding).setStatus("FIXED");
        verify(finding).setRemediationNote("credential rotated");
        verify(finding).setResolvedBy("alice");
        verify(finding).setResolvedAt(longThat(ts -> ts > 0));
        verify(finding).save();
        verify(event).setNote("credential rotated");
        verify(event).save();
    }

    @Test
    public void transitionToOpenClearsResolution() {
        AoSecretFinding finding = mockFinding(2, "FIXED", "PROJ", "my-repo");
        AoRemediationEvent event = mock(AoRemediationEvent.class);
        when(ao.get(AoSecretFinding.class, 2)).thenReturn(finding);
        when(ao.create(eq(AoRemediationEvent.class), any(DBParam.class), any(DBParam.class),
                any(DBParam.class), any(DBParam.class))).thenReturn(event);
        when(authCtx.getCurrentUser()).thenReturn(null);

        service.transition(2, FindingStatus.OPEN, "", null);

        verify(finding).setResolvedBy("");
        verify(finding).setResolvedAt(0L);
    }

    @Test
    public void transitionSameStatusUpdatesNoteOnly() {
        AoSecretFinding finding = mockFinding(3, "FIXED", "PROJ", "repo");
        when(ao.get(AoSecretFinding.class, 3)).thenReturn(finding);

        boolean result = service.transition(3, FindingStatus.FIXED, "updated note", null);

        assertTrue(result);
        verify(finding).setRemediationNote("updated note");
        verify(finding).save();
        // no status change, no event created
        verify(ao, never()).create(eq(AoRemediationEvent.class),
                any(DBParam.class), any(DBParam.class), any(DBParam.class), any(DBParam.class));
    }

    @Test
    public void transitionSameStatusNoNoteDoesNothing() {
        AoSecretFinding finding = mockFinding(4, "OPEN", "PROJ", "repo");
        when(ao.get(AoSecretFinding.class, 4)).thenReturn(finding);

        service.transition(4, FindingStatus.OPEN, null, null);

        verify(finding, never()).save();
    }

    @Test
    public void transitionFalsePositiveSetsResolved() {
        AoSecretFinding finding = mockFinding(5, "OPEN", "PROJ", "repo");
        AoRemediationEvent event = mock(AoRemediationEvent.class);
        when(ao.get(AoSecretFinding.class, 5)).thenReturn(finding);
        when(ao.create(eq(AoRemediationEvent.class), any(DBParam.class), any(DBParam.class),
                any(DBParam.class), any(DBParam.class))).thenReturn(event);
        ApplicationUser user = mock(ApplicationUser.class);
        when(user.getName()).thenReturn("bob");
        when(authCtx.getCurrentUser()).thenReturn(user);

        service.transition(5, FindingStatus.FALSE_POSITIVE, "test credential", null);

        verify(finding).setResolvedBy("bob");
        verify(finding, never()).setResolvedAt(0L);
    }

    @Test
    public void transitionWithContextRecordsIpAndAgent() {
        AoSecretFinding finding = mockFinding(6, "OPEN", "PROJ", "repo");
        AoRemediationEvent event = mock(AoRemediationEvent.class);
        when(ao.get(AoSecretFinding.class, 6)).thenReturn(finding);
        when(ao.create(eq(AoRemediationEvent.class), any(DBParam.class), any(DBParam.class),
                any(DBParam.class), any(DBParam.class))).thenReturn(event);
        when(authCtx.getCurrentUser()).thenReturn(null);

        RemediationContext ctx = new RemediationContext();
        ctx.setSourceIp("192.168.1.10");
        ctx.setUserAgent("Mozilla/5.0");
        service.transition(6, FindingStatus.FIXED, "", ctx);

        verify(event).setSourceIp("192.168.1.10");
        verify(event).setUserAgent("Mozilla/5.0");
    }

    @Test
    public void nullAuthContextFallsBackToSystem() {
        RemediationService noAuth = new RemediationService(ao, null);
        AoSecretFinding finding = mockFinding(7, "OPEN", "PROJ", "repo");
        AoRemediationEvent event = mock(AoRemediationEvent.class);
        when(ao.get(AoSecretFinding.class, 7)).thenReturn(finding);
        when(ao.create(eq(AoRemediationEvent.class), any(DBParam.class), any(DBParam.class),
                any(DBParam.class), any(DBParam.class))).thenReturn(event);

        noAuth.transition(7, FindingStatus.FIXED, "", null);

        // should not throw; actor defaults to "system"
        verify(event).save();
    }

    // ---- trimTo (private helper) ----

    @Test
    public void trimToShortensLongStrings() throws Exception {
        String result = invokeTrimTo("a".repeat(200), 64);
        assertEquals(64, result.length());
    }

    @Test
    public void trimToPreservesShortStrings() throws Exception {
        assertEquals("hello", invokeTrimTo("hello", 64));
    }

    @Test
    public void trimToHandlesNull() throws Exception {
        assertEquals("", invokeTrimTo(null, 64));
    }

    @Test
    public void trimToHandlesExactLength() throws Exception {
        String s = "x".repeat(64);
        assertEquals(s, invokeTrimTo(s, 64));
    }

    // ---- helpers ----

    private AoSecretFinding mockFinding(int id, String status, String projectKey, String repoSlug) {
        AoSecretFinding f = mock(AoSecretFinding.class);
        when(f.getID()).thenReturn(id);
        when(f.getStatus()).thenReturn(status);
        when(f.getProjectKey()).thenReturn(projectKey);
        when(f.getRepositorySlug()).thenReturn(repoSlug);
        return f;
    }

    private String invokeTrimTo(String value, int maxLen) throws Exception {
        Method m = RemediationService.class.getDeclaredMethod("trimTo", String.class, int.class);
        m.setAccessible(true);
        return (String) m.invoke(service, value, maxLen);
    }
}
