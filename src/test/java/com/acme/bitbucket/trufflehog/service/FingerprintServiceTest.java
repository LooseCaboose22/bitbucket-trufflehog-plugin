package com.acme.bitbucket.trufflehog.service;

import com.acme.bitbucket.trufflehog.model.TruffleHogRawFinding;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class FingerprintServiceTest {

    private FingerprintService service;

    @Before
    public void setUp() {
        service = new FingerprintService();
    }

    @Test
    public void fingerprintIsDeterministic() {
        TruffleHogRawFinding raw = finding("AWS", "AWSKeyDetector", "src/config.ts", "AKIA***REDACTED");
        String fp1 = service.fingerprintFor(42, raw);
        String fp2 = service.fingerprintFor(42, raw);
        assertEquals(fp1, fp2);
    }

    @Test
    public void fingerprintIs64HexChars() {
        TruffleHogRawFinding raw = finding("Github", "GithubTokenDetector", ".env", "ghp_***");
        String fp = service.fingerprintFor(1, raw);
        assertEquals(64, fp.length());
        assertTrue("Must be lowercase hex", fp.matches("[0-9a-f]{64}"));
    }

    @Test
    public void differentRepositoryIdGivesDifferentFingerprint() {
        TruffleHogRawFinding raw = finding("Slack", "SlackDetector", "notify.sh", "xoxb-***");
        String fp1 = service.fingerprintFor(1, raw);
        String fp2 = service.fingerprintFor(2, raw);
        assertNotEquals("Same finding in different repos must differ", fp1, fp2);
    }

    @Test
    public void differentFileGivesDifferentFingerprint() {
        TruffleHogRawFinding a = finding("AWS", "AWSKeyDetector", "src/a.ts", "AKIA***");
        TruffleHogRawFinding b = finding("AWS", "AWSKeyDetector", "src/b.ts", "AKIA***");
        assertNotEquals(service.fingerprintFor(1, a), service.fingerprintFor(1, b));
    }

    @Test
    public void differentRedactedGivesDifferentFingerprint() {
        TruffleHogRawFinding a = finding("AWS", "AWSKeyDetector", "cfg.ts", "AKIA111");
        TruffleHogRawFinding b = finding("AWS", "AWSKeyDetector", "cfg.ts", "AKIA222");
        assertNotEquals(service.fingerprintFor(1, a), service.fingerprintFor(1, b));
    }

    @Test
    public void commitHashIsIgnoredForFingerprint() {
        TruffleHogRawFinding a = finding("AWS", "AWSKeyDetector", "cfg.ts", "AKIA***");
        a.setCommit("abc123");
        TruffleHogRawFinding b = finding("AWS", "AWSKeyDetector", "cfg.ts", "AKIA***");
        b.setCommit("def456");
        assertEquals("Commit must not affect fingerprint", service.fingerprintFor(1, a), service.fingerprintFor(1, b));
    }

    @Test
    public void branchIsIgnoredForFingerprint() {
        TruffleHogRawFinding a = finding("AWS", "AWSKeyDetector", "cfg.ts", "AKIA***");
        a.setBranch("main");
        TruffleHogRawFinding b = finding("AWS", "AWSKeyDetector", "cfg.ts", "AKIA***");
        b.setBranch("feature/rotate-keys");
        assertEquals("Branch must not affect fingerprint", service.fingerprintFor(1, a), service.fingerprintFor(1, b));
    }

    @Test
    public void nullFieldsProduceValidFingerprint() {
        TruffleHogRawFinding raw = new TruffleHogRawFinding();
        // all fields null / default
        String fp = service.fingerprintFor(0, raw);
        assertNotNull(fp);
        assertEquals(64, fp.length());
    }

    @Test
    public void nullDetectorTypeHandled() {
        TruffleHogRawFinding a = finding(null, "AWSKeyDetector", "cfg.ts", "AKIA***");
        TruffleHogRawFinding b = finding("",   "AWSKeyDetector", "cfg.ts", "AKIA***");
        assertEquals("null and empty detector type should produce same fingerprint",
                service.fingerprintFor(1, a), service.fingerprintFor(1, b));
    }

    // ---- helpers ----

    private TruffleHogRawFinding finding(String detectorType, String detectorName, String filePath, String redacted) {
        TruffleHogRawFinding f = new TruffleHogRawFinding();
        f.setDetectorType(detectorType);
        f.setDetectorName(detectorName);
        f.setFilePath(filePath);
        f.setRedacted(redacted);
        return f;
    }
}
