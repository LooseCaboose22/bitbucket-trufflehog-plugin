package com.acme.bitbucket.trufflehog.servlet;

import com.acme.bitbucket.trufflehog.model.ScanScope;
import com.acme.bitbucket.trufflehog.service.PluginPermissionService;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.templaterenderer.TemplateRenderer;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.http.HttpServletRequest;

@Named("repositoryDashboardServlet")
@Component
public class RepositoryDashboardServlet extends BaseDashboardServlet {

    @Inject
    public RepositoryDashboardServlet(PluginPermissionService pluginPermissionService,
                                      @ComponentImport TemplateRenderer templateRenderer) {
        super(pluginPermissionService, templateRenderer);
    }

    @Override
    protected String pageTitle() {
        return "TruffleHog — Repository Secret Findings";
    }

    @Override
    protected ScanScope scope() {
        return ScanScope.REPOSITORY;
    }

    @Override
    protected String templateName() {
        return "repository-dashboard.vm";
    }

    @Override
    protected String checkPermissions(HttpServletRequest req) {
        String projectKey = safe(req.getParameter("projectKey"));
        String repositorySlug = safe(req.getParameter("repositorySlug"));
        if (projectKey.isEmpty() || repositorySlug.isEmpty()) {
            return "Missing projectKey or repositorySlug query parameter.";
        }
        return super.checkPermissions(req);
    }
}
