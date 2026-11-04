package com.acme.bitbucket.trufflehog.service;

import com.acme.bitbucket.trufflehog.model.TrufflehogSettings;
import com.atlassian.bitbucket.repository.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import javax.inject.Named;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Resolves the on-disk path for the bare Git repository backing a Bitbucket
 * {@link Repository}. Bitbucket Data Center stores bare repos at
 * {@code <shared-home>/data/repositories/<repository.id>}.
 */
@Named
@Component
public class RepositoryPathResolverService {
    private static final Logger LOG = LoggerFactory.getLogger(RepositoryPathResolverService.class);

    @Inject
    public RepositoryPathResolverService() {
    }

    public Path resolveRepositoryPath(Repository repository, TrufflehogSettings settings) {
        String configuredRoot = settings == null ? "" : safe(settings.getRepositoriesRoot());

        Path root;
        if (!configuredRoot.isEmpty()) {
            root = Paths.get(configuredRoot);
        } else {
            String sharedHome = System.getProperty("bitbucket.shared.home", "");
            if (!sharedHome.isEmpty()) {
                // bitbucket.shared.home already points to the shared dir itself
                root = Paths.get(sharedHome, "data", "repositories");
            } else {
                String home = System.getProperty("bitbucket.home", ".");
                root = Paths.get(home, "shared", "data", "repositories");
            }
        }

        Path candidate = root.resolve(String.valueOf(repository.getId()));
        if (!Files.exists(candidate)) {
            LOG.warn("Repository path does not exist on disk: {}", candidate);
        }
        return candidate;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
