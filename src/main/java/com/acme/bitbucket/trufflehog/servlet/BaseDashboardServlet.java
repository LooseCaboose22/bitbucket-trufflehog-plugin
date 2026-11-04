package com.acme.bitbucket.trufflehog.servlet;

import com.acme.bitbucket.trufflehog.model.ScanScope;
import com.acme.bitbucket.trufflehog.service.PluginPermissionService;
import com.atlassian.templaterenderer.TemplateRenderer;

import javax.inject.Inject;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public abstract class BaseDashboardServlet extends HttpServlet {

    protected final PluginPermissionService pluginPermissionService;
    protected final TemplateRenderer templateRenderer;

    @Inject
    protected BaseDashboardServlet(PluginPermissionService pluginPermissionService,
                                   TemplateRenderer templateRenderer) {
        this.pluginPermissionService = pluginPermissionService;
        this.templateRenderer = templateRenderer;
    }

    protected abstract String pageTitle();
    protected abstract ScanScope scope();
    protected abstract String templateName();

    protected String checkPermissions(HttpServletRequest req) {
        ScanScope s = scope();
        String projectKey = safe(req.getParameter("projectKey"));
        String repositorySlug = safe(req.getParameter("repositorySlug"));
        boolean ok;
        switch (s) {
            case REPOSITORY:
                ok = pluginPermissionService.canViewRepository(projectKey, repositorySlug);
                break;
            case PROJECT:
                ok = pluginPermissionService.canViewProject(projectKey);
                break;
            case SYSTEM:
            default:
                ok = pluginPermissionService.canViewSystemDashboard();
                break;
        }
        return ok ? null : "You do not have permission to view secret-scanner findings at this scope.";
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String denial = checkPermissions(req);
        if (denial != null) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, denial);
            return;
        }

        String cp = req.getContextPath();
        String projectKey = safe(req.getParameter("projectKey"));
        String repositorySlug = safe(req.getParameter("repositorySlug"));
        boolean canRemediate = scope() == ScanScope.SYSTEM
                ? pluginPermissionService.canAdminister()
                : scope() == ScanScope.PROJECT
                    ? pluginPermissionService.canRemediateProject(projectKey)
                    : pluginPermissionService.canRemediateRepository(projectKey, repositorySlug);

        Map<String, Object> context = new HashMap<>();
        context.put("pageTitle", pageTitle());
        context.put("scope", scope().name());
        context.put("projectKey", projectKey);
        context.put("repositorySlug", repositorySlug);
        context.put("canRemediate", canRemediate);
        context.put("contextPath", cp);
        context.put("restBase", cp + "/rest/th-scanner/1.0/");

        resp.setContentType("text/html;charset=UTF-8");
        templateRenderer.render("templates/" + templateName(), context, resp.getWriter());
    }

    protected String safe(String value) {
        return value == null ? "" : value.replace("'", "").replace("\"", "");
    }
}
