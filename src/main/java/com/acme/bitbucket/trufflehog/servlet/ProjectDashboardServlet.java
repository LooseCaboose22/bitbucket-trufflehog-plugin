package com.acme.bitbucket.trufflehog.servlet;

import com.acme.bitbucket.trufflehog.model.ScanScope;
import com.acme.bitbucket.trufflehog.service.PluginPermissionService;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.templaterenderer.TemplateRenderer;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.http.HttpServletRequest;

@Named("projectDashboardServlet")
@Component
public class ProjectDashboardServlet extends BaseDashboardServlet {

    @Inject
    public ProjectDashboardServlet(PluginPermissionService pluginPermissionService,
                                   @ComponentImport TemplateRenderer templateRenderer) {
        super(pluginPermissionService, templateRenderer);
    }

    @Override
    protected String pageTitle() {
        return "TruffleHog — Project Secret Findings";
    }

    @Override
    protected ScanScope scope() {
        return ScanScope.PROJECT;
    }

    @Override
    protected String templateName() {
        return "project-dashboard.vm";
    }

    @Override
    protected String checkPermissions(HttpServletRequest req) {
        String projectKey = safe(req.getParameter("projectKey"));
        if (projectKey.isEmpty()) {
            return "Missing projectKey query parameter.";
        }
        return super.checkPermissions(req);
    }
}
