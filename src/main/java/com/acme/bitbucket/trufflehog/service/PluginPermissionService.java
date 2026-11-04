package com.acme.bitbucket.trufflehog.service;

import com.atlassian.bitbucket.permission.Permission;
import com.atlassian.bitbucket.permission.PermissionService;
import com.atlassian.bitbucket.project.Project;
import com.atlassian.bitbucket.project.ProjectService;
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.bitbucket.repository.RepositoryService;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import javax.inject.Named;

/**
 * Centralises permission checks: who can view findings, scan, remediate, etc.
 *
 * <ul>
 *   <li>System dashboard: SYS_ADMIN</li>
 *   <li>Project dashboard: PROJECT_READ (any user with read access)</li>
 *   <li>Project scan trigger / remediate: PROJECT_ADMIN</li>
 *   <li>Repository dashboard: REPO_READ</li>
 *   <li>Repository scan trigger / remediate: REPO_ADMIN</li>
 * </ul>
 */
@Named
@Component
public class PluginPermissionService {

    private final PermissionService permissionService;
    private final ProjectService projectService;
    private final RepositoryService repositoryService;

    @Inject
    public PluginPermissionService(
            @ComponentImport PermissionService permissionService,
            @ComponentImport ProjectService projectService,
            @ComponentImport RepositoryService repositoryService) {
        this.permissionService = permissionService;
        this.projectService = projectService;
        this.repositoryService = repositoryService;
    }

    public boolean canViewSystemDashboard() {
        return permissionService.hasGlobalPermission(Permission.SYS_ADMIN);
    }

    public boolean canAdminister() {
        return permissionService.hasGlobalPermission(Permission.SYS_ADMIN);
    }

    public boolean canViewProject(String projectKey) {
        if (projectKey == null || projectKey.isEmpty()) {
            return false;
        }
        if (canViewSystemDashboard()) {
            return true;
        }
        Project project = projectService.getByKey(projectKey);
        return project != null && permissionService.hasProjectPermission(project, Permission.PROJECT_READ);
    }

    public boolean canRemediateProject(String projectKey) {
        if (canAdminister()) {
            return true;
        }
        Project project = projectKey == null ? null : projectService.getByKey(projectKey);
        return project != null && permissionService.hasProjectPermission(project, Permission.PROJECT_ADMIN);
    }

    public boolean canViewRepository(String projectKey, String repositorySlug) {
        if (canViewProject(projectKey)) {
            return true;
        }
        Repository repository = repositoryService.getBySlug(projectKey, repositorySlug);
        return repository != null && permissionService.hasRepositoryPermission(repository, Permission.REPO_READ);
    }

    public boolean canRemediateRepository(String projectKey, String repositorySlug) {
        if (canRemediateProject(projectKey)) {
            return true;
        }
        Repository repository = repositoryService.getBySlug(projectKey, repositorySlug);
        return repository != null && permissionService.hasRepositoryPermission(repository, Permission.REPO_ADMIN);
    }
}
