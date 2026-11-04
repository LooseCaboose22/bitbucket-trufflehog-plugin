package com.acme.bitbucket.trufflehog.service;

import com.acme.bitbucket.trufflehog.model.TruffleHogRawFinding;
import com.acme.bitbucket.trufflehog.model.TrufflehogSettings;
import com.atlassian.bitbucket.repository.Repository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Named
@Component
public class TrufflehogScannerService {
    private static final Logger LOG = LoggerFactory.getLogger(TrufflehogScannerService.class);
    private static final Pattern UNSAFE_ARG = Pattern.compile("[\\$`;&|<>()\\n\\r]");

    private final RepositoryPathResolverService repositoryPathResolverService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Inject
    public TrufflehogScannerService(RepositoryPathResolverService repositoryPathResolverService) {
        this.repositoryPathResolverService = repositoryPathResolverService;
    }

    public List<TruffleHogRawFinding> scanRepository(Repository repository, TrufflehogSettings settings) {
        return scanRepository(repository, settings, null, null);
    }

    public List<TruffleHogRawFinding> scanRepository(Repository repository,
                                                     TrufflehogSettings settings,
                                                     String fromCommit,
                                                     String toCommit) {
        Path repositoryPath = repositoryPathResolverService.resolveRepositoryPath(repository, settings);
        if (repositoryPath == null) {
            LOG.warn("Could not resolve repository path for {} - skipping scan", repository.getId());
            return Collections.emptyList();
        }

        List<String> command = new ArrayList<>();
        command.add(safeArg(settings.getTrufflehogExecutable(), "trufflehog"));
        command.add("git");
        command.add("--json");
        command.add("--no-update");

        if (fromCommit != null && !fromCommit.isEmpty()) {
            command.add("--since-commit");
            command.add(safeArg(fromCommit, ""));
        }
        if (toCommit != null && !toCommit.isEmpty()) {
            command.add("--branch");
            command.add(safeArg(toCommit, ""));
        }

        // Custom detector config file (v1.4)
        String configPath = settings.getCustomDetectorConfigPath();
        if (configPath != null && !configPath.trim().isEmpty()) {
            command.add("--config=" + safeArg(configPath.trim(), ""));
        }

        String extra = settings.getExtraArgs();
        if (extra != null && !extra.trim().isEmpty()) {
            for (String token : extra.trim().split("\\s+")) {
                command.add(safeArg(token, ""));
            }
        }

        String allowlist = settings.getDetectorAllowlist();
        if (allowlist != null && !allowlist.trim().isEmpty()) {
            command.add("--include-detectors=" + sanitizeDetectorList(allowlist));
        }
        String ignorelist = settings.getDetectorIgnorelist();
        if (ignorelist != null && !ignorelist.trim().isEmpty()) {
            command.add("--exclude-detectors=" + sanitizeDetectorList(ignorelist));
        }

        // Max file size (v1.2)
        int maxFileSizeKb = settings.getMaxFileSizeKb();
        if (maxFileSizeKb > 0) {
            command.add("--max-archive-size=" + (maxFileSizeKb * 1024));
        }

        Path tempClone = null;
        Process process = null;
        try {
            tempClone = Files.createTempDirectory("th-scan-" + repository.getId() + "-");
            Process cloneProc = new ProcessBuilder(
                    "git", "clone", "--quiet",
                    repositoryPath.toAbsolutePath().toString(),
                    tempClone.toAbsolutePath().toString())
                    .redirectErrorStream(true)
                    .start();
            boolean cloned = cloneProc.waitFor(5, TimeUnit.MINUTES);
            if (!cloned || cloneProc.exitValue() != 0) {
                LOG.warn("git clone failed for {}/{}: {}", repository.getProject().getKey(),
                        repository.getSlug(), drainStream(cloneProc.getInputStream()));
                return Collections.emptyList();
            }

            command.add(tempClone.toUri().toString());
            LOG.info("Running trufflehog: {}", String.join(" ", command));

            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(false);

            // Proxy environment variables (v1.5)
            applyProxyEnv(processBuilder.environment(), settings);

            process = processBuilder.start();
            List<TruffleHogRawFinding> findings = applyWarnOnlyDetectors(
                    applyFileExclusions(
                            applyVerifiedFilter(parseOutput(process), settings.getVerifiedFilter()),
                            settings),
                    settings);

            boolean finished = process.waitFor(Math.max(1, settings.getScanTimeoutMinutes()), TimeUnit.MINUTES);
            if (!finished) {
                LOG.warn("TruffleHog timed out for {}/{}", repository.getProject().getKey(), repository.getSlug());
                process.destroyForcibly();
                return findings;
            }
            int exit = process.exitValue();
            if (exit != 0) {
                LOG.warn("TruffleHog exited {} for {}/{}: {}",
                        exit, repository.getProject().getKey(), repository.getSlug(),
                        drainStream(process.getErrorStream()));
            }
            return findings;
        } catch (IOException ioe) {
            LOG.error("Failed to invoke TruffleHog binary", ioe);
            return Collections.emptyList();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            if (process != null) process.destroyForcibly();
            return Collections.emptyList();
        } finally {
            if (tempClone != null) deleteRecursively(tempClone.toFile());
        }
    }

    private void applyProxyEnv(Map<String, String> env, TrufflehogSettings settings) {
        String proxyUrl = settings.getProxyUrl();
        if (proxyUrl == null || proxyUrl.trim().isEmpty()) return;
        env.put("HTTP_PROXY",  proxyUrl.trim());
        env.put("HTTPS_PROXY", proxyUrl.trim());
        env.put("http_proxy",  proxyUrl.trim());
        env.put("https_proxy", proxyUrl.trim());
        String noProxy = settings.getProxyNoProxy();
        if (noProxy != null && !noProxy.trim().isEmpty()) {
            env.put("NO_PROXY",  noProxy.trim());
            env.put("no_proxy",  noProxy.trim());
        }
        String sslCert = settings.getProxySslCertPath();
        if (sslCert != null && !sslCert.trim().isEmpty()) {
            env.put("SSL_CERT_FILE", sslCert.trim());
        }
    }

    /** Mark findings from warn-only detectors with WARN severity prefix so they never block. */
    private List<TruffleHogRawFinding> applyWarnOnlyDetectors(List<TruffleHogRawFinding> findings,
                                                               TrufflehogSettings settings) {
        String warnOnly = settings.getWarnOnlyDetectors();
        if (warnOnly == null || warnOnly.trim().isEmpty()) return findings;
        Set<String> warnSet = new HashSet<>();
        for (String t : warnOnly.split("[,;\\n\\r]+")) {
            String d = t.trim();
            if (!d.isEmpty()) warnSet.add(d.toLowerCase());
        }
        for (TruffleHogRawFinding f : findings) {
            String name = f.getDetectorName();
            if (name != null && warnSet.contains(name.toLowerCase())) {
                f.setWarnOnly(true);
            }
        }
        return findings;
    }

    private List<TruffleHogRawFinding> parseOutput(Process process) throws IOException {
        List<TruffleHogRawFinding> findings = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || line.charAt(0) != '{') continue;
                JsonNode node;
                try { node = objectMapper.readTree(line); }
                catch (Exception e) { LOG.debug("Skipping non-JSON line"); continue; }

                TruffleHogRawFinding f = new TruffleHogRawFinding();
                f.setDetectorType(text(node, "DetectorType"));
                f.setDetectorName(text(node, "DetectorName"));
                f.setVerified(node.path("Verified").asBoolean(false));
                f.setRedacted(text(node, "Redacted"));
                f.setRaw(text(node, "Raw"));

                JsonNode git = node.path("SourceMetadata").path("Data").path("Git");
                if (git.isObject() && !git.isMissingNode()) {
                    f.setFilePath(git.path("file").asText(""));
                    f.setLineNumber(git.path("line").asInt(0));
                    f.setBranch(git.path("branch").asText(""));
                    f.setCommit(git.path("commit").asText(""));
                    f.setCommitAuthor(git.path("email").asText(""));
                    String ts = git.path("timestamp").asText("");
                    if (!ts.isEmpty()) {
                        try { f.setCommitTimestamp(OffsetDateTime.parse(ts).toInstant().toEpochMilli()); }
                        catch (Exception ignored) { f.setCommitTimestamp(0L); }
                    }
                } else {
                    JsonNode fs = node.path("SourceMetadata").path("Data").path("Filesystem");
                    f.setFilePath(fs.path("file").asText(""));
                    f.setLineNumber(fs.path("line").asInt(0));
                }
                findings.add(f);
            }
        }
        return findings;
    }

    private String text(JsonNode node, String name) {
        JsonNode v = node.path(name);
        return v.isMissingNode() ? "" : v.asText("");
    }

    private String drainStream(java.io.InputStream input) {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        } catch (IOException ignored) { return ""; }
    }

    private String safeArg(String value, String fallback) {
        if (value == null || value.isEmpty()) return fallback;
        if (UNSAFE_ARG.matcher(value).find()) {
            LOG.warn("Rejecting unsafe TruffleHog argument: {}", value);
            return fallback;
        }
        return value;
    }

    private List<TruffleHogRawFinding> applyFileExclusions(List<TruffleHogRawFinding> findings,
                                                           TrufflehogSettings settings) {
        String extRaw  = settings.getExcludedExtensions();
        String dirRaw  = settings.getExcludedDirectories();
        String allowRaw = settings.getAllowlistedPaths();

        if ((extRaw == null || extRaw.trim().isEmpty()) && (dirRaw == null || dirRaw.trim().isEmpty())) {
            return findings;
        }

        Set<String> excludedExts = new HashSet<>();
        if (extRaw != null) {
            for (String t : extRaw.split("[,;\\n\\r]+")) {
                String e = t.trim().toLowerCase();
                if (!e.isEmpty()) excludedExts.add(e.startsWith(".") ? e : "." + e);
            }
        }
        List<String> excludedDirs = new ArrayList<>();
        if (dirRaw != null) {
            for (String t : dirRaw.split("[,;\\n\\r]+")) {
                String d = t.trim();
                if (!d.isEmpty()) excludedDirs.add(d.endsWith("/") ? d : d + "/");
            }
        }
        List<String> allowPaths = new ArrayList<>();
        if (allowRaw != null) {
            for (String t : allowRaw.split("[,;\\n\\r]+")) {
                String p = t.trim();
                if (!p.isEmpty()) allowPaths.add(p);
            }
        }

        List<TruffleHogRawFinding> result = new ArrayList<>();
        for (TruffleHogRawFinding f : findings) {
            String fp = f.getFilePath();
            if (fp == null || fp.isEmpty()) { result.add(f); continue; }

            boolean inAllowlist = false;
            for (String ap : allowPaths) { if (fp.startsWith(ap)) { inAllowlist = true; break; } }
            if (inAllowlist) { result.add(f); continue; }

            String lower = fp.toLowerCase();
            boolean excluded = false;
            for (String ext : excludedExts) { if (lower.endsWith(ext)) { excluded = true; break; } }
            if (excluded) continue;

            for (String dir : excludedDirs) {
                if (fp.startsWith(dir) || fp.contains("/" + dir)) { excluded = true; break; }
            }
            if (!excluded) result.add(f);
        }
        return result;
    }

    private List<TruffleHogRawFinding> applyVerifiedFilter(List<TruffleHogRawFinding> findings, String filter) {
        if (filter == null || filter.isEmpty() || "BOTH".equalsIgnoreCase(filter)) return findings;
        List<TruffleHogRawFinding> result = new ArrayList<>();
        for (TruffleHogRawFinding f : findings) {
            if ("VERIFIED_ONLY".equalsIgnoreCase(filter) && f.isVerified()) result.add(f);
            else if ("UNVERIFIED_ONLY".equalsIgnoreCase(filter) && !f.isVerified()) result.add(f);
        }
        return result;
    }

    private String sanitizeDetectorList(String raw) {
        StringBuilder sb = new StringBuilder();
        for (String token : raw.split("[,\\n\\r]+")) {
            String t = token.trim();
            if (t.isEmpty()) continue;
            if (UNSAFE_ARG.matcher(t).find()) { LOG.warn("Ignoring unsafe detector name: {}", t); continue; }
            if (sb.length() > 0) sb.append(',');
            sb.append(t);
        }
        return sb.toString();
    }

    public List<String> safeArgsForTest(String extraArgs) {
        return Arrays.asList(extraArgs.split("\\s+"));
    }

    private void deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) { for (File child : children) deleteRecursively(child); }
        }
        if (!file.delete()) LOG.debug("Could not delete temp file: {}", file);
    }
}
