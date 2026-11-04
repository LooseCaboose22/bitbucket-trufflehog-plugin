package com.acme.bitbucket.trufflehog.servlet;

import com.acme.bitbucket.trufflehog.service.PluginPermissionService;
import com.atlassian.templaterenderer.TemplateRenderer;
import org.junit.Before;
import org.junit.Test;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class BaseDashboardServletTest {

    private PluginPermissionService perms;
    private TemplateRenderer templateRenderer;
    private SystemDashboardServlet servlet;

    @Before
    public void setUp() {
        perms = mock(PluginPermissionService.class);
        templateRenderer = mock(TemplateRenderer.class);
        servlet = new SystemDashboardServlet(perms, templateRenderer);
    }

    // ---- permission denial ----

    @Test
    public void deniedWhenCannotViewSystemDashboard() throws Exception {
        when(perms.canViewSystemDashboard()).thenReturn(false);
        HttpServletRequest req = req("/bitbucket");
        HttpServletResponse resp = mock(HttpServletResponse.class);

        servlet.doGet(req, resp);

        verify(resp).sendError(eq(HttpServletResponse.SC_FORBIDDEN), anyString());
        verify(templateRenderer, never()).render(anyString(), any(), any());
    }

    // ---- template delegation ----

    @Test
    @SuppressWarnings("unchecked")
    public void delegatesToSystemDashboardTemplate() throws Exception {
        when(perms.canViewSystemDashboard()).thenReturn(true);
        when(perms.canAdminister()).thenReturn(false);

        HttpServletRequest req = req("/bitbucket");
        HttpServletResponse resp = mock(HttpServletResponse.class);
        when(resp.getWriter()).thenReturn(new PrintWriter(new StringWriter()));

        servlet.doGet(req, resp);

        verify(templateRenderer).render(eq("templates/system-dashboard.vm"), any(Map.class), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void contextContainsScopeAndContextPath() throws Exception {
        when(perms.canViewSystemDashboard()).thenReturn(true);
        when(perms.canAdminister()).thenReturn(true);

        HttpServletRequest req = req("/bitbucket");
        HttpServletResponse resp = mock(HttpServletResponse.class);
        when(resp.getWriter()).thenReturn(new PrintWriter(new StringWriter()));

        final Map<String, Object>[] captured = new Map[1];
        doAnswer(inv -> { captured[0] = inv.getArgument(1); return null; })
                .when(templateRenderer).render(anyString(), any(Map.class), any());

        servlet.doGet(req, resp);

        Map<String, Object> ctx = captured[0];
        assertEquals("SYSTEM",        ctx.get("scope"));
        assertEquals("/bitbucket",    ctx.get("contextPath"));
        assertEquals(Boolean.TRUE,    ctx.get("canRemediate"));
        assertTrue(ctx.get("restBase").toString().startsWith("/bitbucket"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void contextPathPropagatedToRestBase() throws Exception {
        when(perms.canViewSystemDashboard()).thenReturn(true);
        when(perms.canAdminister()).thenReturn(false);

        HttpServletRequest req = req("/mybb");
        HttpServletResponse resp = mock(HttpServletResponse.class);
        when(resp.getWriter()).thenReturn(new PrintWriter(new StringWriter()));

        final Map<String, Object>[] captured = new Map[1];
        doAnswer(inv -> { captured[0] = inv.getArgument(1); return null; })
                .when(templateRenderer).render(anyString(), any(Map.class), any());

        servlet.doGet(req, resp);

        assertTrue(captured[0].get("restBase").toString().startsWith("/mybb"));
    }

    // ---- safe() helper ----

    @Test
    public void safeStripsSingleAndDoubleQuotes() throws Exception {
        java.lang.reflect.Method m = BaseDashboardServlet.class
                .getDeclaredMethod("safe", String.class);
        m.setAccessible(true);
        assertEquals("abc",  m.invoke(servlet, "a'b\"c"));
        assertEquals("",     m.invoke(servlet, (Object) null));
    }

    // ---- helpers ----

    private HttpServletRequest req(String contextPath) {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getContextPath()).thenReturn(contextPath);
        when(req.getParameter("projectKey")).thenReturn(null);
        when(req.getParameter("repositorySlug")).thenReturn(null);
        return req;
    }
}
