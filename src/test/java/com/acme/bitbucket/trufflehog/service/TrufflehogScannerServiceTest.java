package com.acme.bitbucket.trufflehog.service;

import com.acme.bitbucket.trufflehog.model.TruffleHogRawFinding;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TrufflehogScannerServiceTest {

    private TrufflehogScannerService service;

    @Before
    public void setUp() {
        RepositoryPathResolverService resolver = mock(RepositoryPathResolverService.class);
        service = new TrufflehogScannerService(resolver);
    }

    // ---- JSON parsing ----

    @Test
    public void parsesVerifiedAwsFinding() throws Exception {
        String jsonl =
            "{\"DetectorType\":\"AWS\",\"DetectorName\":\"AWSKeyDetector\",\"Verified\":true," +
            "\"Redacted\":\"AKIA****EXAMPLE\",\"Raw\":\"AKIA1234567890ABCDEF\"," +
            "\"SourceMetadata\":{\"Data\":{\"Git\":{\"file\":\"src/config.ts\"," +
            "\"line\":12,\"branch\":\"main\",\"commit\":\"abc123def456\"," +
            "\"email\":\"dev@example.com\",\"timestamp\":\"2024-01-15T10:00:00Z\"}}}}";

        List<TruffleHogRawFinding> findings = parseOutput(jsonl);

        assertEquals(1, findings.size());
        TruffleHogRawFinding f = findings.get(0);
        assertEquals("AWS",               f.getDetectorType());
        assertEquals("AWSKeyDetector",    f.getDetectorName());
        assertTrue(f.isVerified());
        assertEquals("AKIA****EXAMPLE",   f.getRedacted());
        assertEquals("src/config.ts",     f.getFilePath());
        assertEquals(12,                  f.getLineNumber());
        assertEquals("main",              f.getBranch());
        assertEquals("abc123def456",      f.getCommit());
        assertEquals("dev@example.com",   f.getCommitAuthor());
        assertTrue(f.getCommitTimestamp() > 0);
    }

    @Test
    public void parsesUnverifiedFinding() throws Exception {
        String jsonl =
            "{\"DetectorType\":\"Slack\",\"DetectorName\":\"SlackDetector\",\"Verified\":false," +
            "\"Redacted\":\"xoxb-****\",\"Raw\":\"xoxb-token\"," +
            "\"SourceMetadata\":{\"Data\":{\"Git\":{\"file\":\"notify.sh\",\"line\":3," +
            "\"branch\":\"develop\",\"commit\":\"deadbeef\",\"email\":\"ci@example.com\"," +
            "\"timestamp\":\"2024-06-01T08:30:00+02:00\"}}}}";

        List<TruffleHogRawFinding> findings = parseOutput(jsonl);

        assertEquals(1, findings.size());
        assertFalse(findings.get(0).isVerified());
        assertEquals("Slack", findings.get(0).getDetectorType());
        assertEquals("develop", findings.get(0).getBranch());
    }

    @Test
    public void parsesMultipleFindings() throws Exception {
        String line1 = "{\"DetectorType\":\"AWS\",\"DetectorName\":\"AWSKeyDetector\",\"Verified\":true," +
                       "\"Redacted\":\"AKIA***\",\"SourceMetadata\":{\"Data\":{\"Git\":{\"file\":\"a.ts\",\"line\":1,\"branch\":\"main\",\"commit\":\"aaa\",\"email\":\"\",\"timestamp\":\"2024-01-01T00:00:00Z\"}}}}";
        String line2 = "{\"DetectorType\":\"Github\",\"DetectorName\":\"GithubTokenDetector\",\"Verified\":false," +
                       "\"Redacted\":\"ghp_***\",\"SourceMetadata\":{\"Data\":{\"Git\":{\"file\":\".env\",\"line\":5,\"branch\":\"feat\",\"commit\":\"bbb\",\"email\":\"\",\"timestamp\":\"2024-01-02T00:00:00Z\"}}}}";

        List<TruffleHogRawFinding> findings = parseOutput(line1 + "\n" + line2);

        assertEquals(2, findings.size());
        assertEquals("AWS",    findings.get(0).getDetectorType());
        assertEquals("Github", findings.get(1).getDetectorType());
    }

    @Test
    public void skipsNonJsonLines() throws Exception {
        String input = "trufflehog v3.82.0\n" +
                       "{\"DetectorType\":\"AWS\",\"DetectorName\":\"AWSKeyDetector\",\"Verified\":true," +
                       "\"Redacted\":\"AKIA***\",\"SourceMetadata\":{\"Data\":{\"Git\":{\"file\":\"cfg.ts\",\"line\":1,\"branch\":\"main\",\"commit\":\"aaa\",\"email\":\"\",\"timestamp\":\"2024-01-01T00:00:00Z\"}}}}\n" +
                       "time=\"2024-01-01T10:00:00Z\" level=info msg=\"Finished scanning\"";

        List<TruffleHogRawFinding> findings = parseOutput(input);

        assertEquals("Non-JSON lines must be skipped", 1, findings.size());
    }

    @Test
    public void skipsMalformedJsonLines() throws Exception {
        String input = "{\"DetectorType\":\"AWS\",\"DetectorName\":\"AWSKeyDetector\",\"Verified\":true," +
                       "\"Redacted\":\"AKIA***\",\"SourceMetadata\":{\"Data\":{\"Git\":{\"file\":\"a.ts\",\"line\":1,\"branch\":\"main\",\"commit\":\"aaa\",\"email\":\"\",\"timestamp\":\"2024-01-01T00:00:00Z\"}}}}\n" +
                       "{this is not valid json}\n" +
                       "{\"DetectorType\":\"Slack\",\"DetectorName\":\"SlackDetector\",\"Verified\":false," +
                       "\"Redacted\":\"xoxb-***\",\"SourceMetadata\":{\"Data\":{\"Git\":{\"file\":\"b.ts\",\"line\":2,\"branch\":\"main\",\"commit\":\"bbb\",\"email\":\"\",\"timestamp\":\"2024-01-01T00:00:00Z\"}}}}";

        List<TruffleHogRawFinding> findings = parseOutput(input);

        assertEquals("Malformed JSON must be skipped, valid lines kept", 2, findings.size());
    }

    @Test
    public void handlesEmptyOutput() throws Exception {
        List<TruffleHogRawFinding> findings = parseOutput("");
        assertTrue(findings.isEmpty());
    }

    @Test
    public void handlesFilesystemSourceMetadata() throws Exception {
        String jsonl = "{\"DetectorType\":\"Generic\",\"DetectorName\":\"GenericAPIKey\",\"Verified\":false," +
                       "\"Redacted\":\"key-***\"," +
                       "\"SourceMetadata\":{\"Data\":{\"Filesystem\":{\"file\":\"/tmp/secrets.txt\",\"line\":7}}}}";

        List<TruffleHogRawFinding> findings = parseOutput(jsonl);

        assertEquals(1, findings.size());
        assertEquals("/tmp/secrets.txt", findings.get(0).getFilePath());
        assertEquals(7,                  findings.get(0).getLineNumber());
    }

    @Test
    public void malformedTimestampDoesNotThrow() throws Exception {
        String jsonl = "{\"DetectorType\":\"AWS\",\"DetectorName\":\"AWSKeyDetector\",\"Verified\":true," +
                       "\"Redacted\":\"AKIA***\"," +
                       "\"SourceMetadata\":{\"Data\":{\"Git\":{\"file\":\"a.ts\",\"line\":1,\"branch\":\"main\",\"commit\":\"aaa\",\"email\":\"\",\"timestamp\":\"not-a-date\"}}}}";

        List<TruffleHogRawFinding> findings = parseOutput(jsonl);

        assertEquals(1, findings.size());
        assertEquals(0L, findings.get(0).getCommitTimestamp());
    }

    // ---- safeArg / UNSAFE_ARG pattern ----

    @Test
    public void rejectsSemicolon() throws Exception {
        String result = invokeSafeArg("trufflehog; rm -rf /", "trufflehog");
        assertEquals("trufflehog", result);
    }

    @Test
    public void rejectsPipe() throws Exception {
        assertEquals("fallback", invokeSafeArg("trufflehog|bash", "fallback"));
    }

    @Test
    public void rejectsBacktick() throws Exception {
        assertEquals("safe", invokeSafeArg("`evil`", "safe"));
    }

    @Test
    public void rejectsNewline() throws Exception {
        assertEquals("safe", invokeSafeArg("arg\nwith\nnewlines", "safe"));
    }

    @Test
    public void acceptsNormalPath() throws Exception {
        assertEquals("/usr/local/bin/trufflehog", invokeSafeArg("/usr/local/bin/trufflehog", "fallback"));
    }

    @Test
    public void acceptsNormalFlags() throws Exception {
        assertEquals("--only-verified", invokeSafeArg("--only-verified", "fallback"));
        assertEquals("--no-update",     invokeSafeArg("--no-update",     "fallback"));
    }

    @Test
    public void nullValueReturnsFallback() throws Exception {
        assertEquals("default", invokeSafeArg(null, "default"));
    }

    @Test
    public void emptyValueReturnsFallback() throws Exception {
        assertEquals("default", invokeSafeArg("", "default"));
    }

    // ---- helpers ----

    @SuppressWarnings("unchecked")
    private List<TruffleHogRawFinding> parseOutput(String jsonl) throws Exception {
        Process process = mock(Process.class);
        InputStream is = new ByteArrayInputStream(jsonl.getBytes(StandardCharsets.UTF_8));
        when(process.getInputStream()).thenReturn(is);

        Method m = TrufflehogScannerService.class.getDeclaredMethod("parseOutput", Process.class);
        m.setAccessible(true);
        return (List<TruffleHogRawFinding>) m.invoke(service, process);
    }

    private String invokeSafeArg(String value, String fallback) throws Exception {
        Method m = TrufflehogScannerService.class.getDeclaredMethod("safeArg", String.class, String.class);
        m.setAccessible(true);
        return (String) m.invoke(service, value, fallback);
    }
}
