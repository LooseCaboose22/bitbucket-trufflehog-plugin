package com.acme.bitbucket.trufflehog.servlet;

import com.acme.bitbucket.trufflehog.model.ScanScope;
import com.acme.bitbucket.trufflehog.service.PluginPermissionService;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.templaterenderer.TemplateRenderer;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import javax.inject.Named;

@Named("systemDashboardServlet")
@Component
public class SystemDashboardServlet extends BaseDashboardServlet {

    @Inject
    public SystemDashboardServlet(PluginPermissionService pluginPermissionService,
                                  @ComponentImport TemplateRenderer templateRenderer) {
        super(pluginPermissionService, templateRenderer);
    }

    @Override
    protected String pageTitle() {
        return "TruffleHog — System Dashboard";
    }

    @Override
    protected ScanScope scope() {
        return ScanScope.SYSTEM;
    }

    @Override
    protected String templateName() {
        return "system-dashboard.vm";
    }
}
