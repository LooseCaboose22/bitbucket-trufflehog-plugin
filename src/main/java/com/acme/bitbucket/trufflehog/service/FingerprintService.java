package com.acme.bitbucket.trufflehog.service;

import com.acme.bitbucket.trufflehog.model.TruffleHogRawFinding;
import org.springframework.stereotype.Component;

import javax.inject.Named;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Produces a stable fingerprint for a finding so that re-scans dedupe to the
 * existing record (preserving its status / remediation history). We hash
 * detector + file + redacted secret. The commit hash is deliberately excluded:
 * the same leaked secret in two commits is still the same secret.
 */
@Named
@Component
public class FingerprintService {

    public String fingerprintFor(int repositoryId, TruffleHogRawFinding raw) {
        String input = repositoryId + "|"
                + safe(raw.getDetectorType()) + "|"
                + safe(raw.getDetectorName()) + "|"
                + safe(raw.getFilePath()) + "|"
                + safe(raw.getRedacted());
        return sha256Hex(input);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
