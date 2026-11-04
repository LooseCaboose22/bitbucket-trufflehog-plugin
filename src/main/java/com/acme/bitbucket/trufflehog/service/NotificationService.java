package com.acme.bitbucket.trufflehog.service;

import com.acme.bitbucket.trufflehog.model.TruffleHogRawFinding;
import com.acme.bitbucket.trufflehog.model.TrufflehogSettings;
import com.atlassian.bitbucket.repository.Repository;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.inject.Named;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sends notifications (email via Bitbucket SMTP or direct, Teams/Slack webhooks)
 * for new TruffleHog findings.
 */
@Named
@Component
public class NotificationService {
    private static final Logger LOG = LoggerFactory.getLogger(NotificationService.class);
    private static final String MAIL_MGR_IFACE = "com.atlassian.mail.server.MailServerManager";

    public void notifyNewFindings(Repository repository,
                                  List<TruffleHogRawFinding> newFindings,
                                  TrufflehogSettings settings) {
        if (newFindings == null || newFindings.isEmpty()) return;

        if (settings.isNotifyCommitter()) {
            Map<String, List<TruffleHogRawFinding>> byCommitter = new LinkedHashMap<>();
            for (TruffleHogRawFinding f : newFindings) {
                String email = f.getCommitAuthor();
                if (email != null && email.contains("@")) {
                    byCommitter.computeIfAbsent(email, k -> new ArrayList<>()).add(f);
                }
            }
            for (Map.Entry<String, List<TruffleHogRawFinding>> entry : byCommitter.entrySet()) {
                TruffleHogRawFinding first = entry.getValue().get(0);
                String subject = interpolate(settings.getCommitterSubject(), repository, first, entry.getValue().size());
                String body    = interpolate(settings.getCommitterBody(),    repository, first, entry.getValue().size());
                sendEmail(entry.getKey(), subject, body, settings);
            }
        }

        if (settings.isNotifyAdmins() && !blank(settings.getAdminRecipients())) {
            String subject = interpolate(settings.getAdminSubject(), repository, newFindings.get(0), newFindings.size());
            String body    = buildSummaryBody(repository, newFindings);
            for (String addr : splitRecipients(settings.getAdminRecipients())) {
                sendEmail(addr, subject, body, settings);
            }
        }

        if (settings.isNotifyInfosec() && !blank(settings.getInfosecRecipients())) {
            String subject = interpolate(settings.getInfosecSubject(), repository, newFindings.get(0), newFindings.size());
            String body    = buildSummaryBody(repository, newFindings);
            for (String addr : splitRecipients(settings.getInfosecRecipients())) {
                sendEmail(addr, subject, body, settings);
            }
        }

        // Webhook notifications
        String webhookMsg = buildWebhookMessage(repository, newFindings);
        if (!blank(settings.getTeamsWebhookUrl())) {
            sendTeamsWebhook(settings.getTeamsWebhookUrl(), webhookMsg);
        }
        if (!blank(settings.getSlackWebhookUrl())) {
            sendSlackWebhook(settings.getSlackWebhookUrl(), webhookMsg);
        }
    }

    public boolean sendTestEmail(String to, TrufflehogSettings settings) {
        try {
            sendEmail(to, "[TruffleHog] Test notification", "This is a test notification from the TruffleHog Secret Scanner plugin.", settings);
            return true;
        } catch (Exception ex) {
            LOG.warn("Test email failed to {}: {}", to, ex.getMessage());
            return false;
        }
    }

    public boolean sendTestTeamsWebhook(TrufflehogSettings settings) {
        return sendTeamsWebhook(settings.getTeamsWebhookUrl(), "[TruffleHog] Test notification from Bitbucket Secret Scanner");
    }

    public boolean sendTestSlackWebhook(TrufflehogSettings settings) {
        return sendSlackWebhook(settings.getSlackWebhookUrl(), "[TruffleHog] Test notification from Bitbucket Secret Scanner");
    }

    private void sendEmail(String to, String subject, String body, TrufflehogSettings settings) {
        // Use direct SMTP if smtpHost is configured, otherwise fall back to Bitbucket's mail server
        if (!blank(settings.getSmtpHost())) {
            sendDirectSmtp(to, subject, body, settings);
        } else {
            sendViaBitbucketMailServer(to, subject, body);
        }
    }

    private void sendDirectSmtp(String to, String subject, String body, TrufflehogSettings settings) {
        // Use Bitbucket's mail classloader to send via direct SMTP settings.
        // We build the connection parameters from settings and pass through the existing
        // OSGi-reflected mail path, overriding host/port/auth if the smtp server object
        // supports setHost/setPort methods. Otherwise fall back to the standard path.
        try {
            Bundle bundle = FrameworkUtil.getBundle(getClass());
            if (bundle == null) { LOG.warn("TruffleHog: OSGi bundle context unavailable"); return; }
            BundleContext bc = bundle.getBundleContext();
            ServiceReference<?> smRef = bc.getServiceReference(MAIL_MGR_IFACE);
            if (smRef == null) { LOG.warn("TruffleHog: no MailServerManager found — cannot send to {}", to); return; }
            Object mailMgr = bc.getService(smRef);
            try {
                Method getSmtp = mailMgr.getClass().getMethod("getDefaultSMTPMailServer");
                Object smtp = getSmtp.invoke(mailMgr);
                if (smtp == null) { LOG.warn("TruffleHog: no SMTP server configured"); return; }
                ClassLoader smtpCl = smtp.getClass().getClassLoader();
                Class<?> emailClass = smtpCl.loadClass("com.atlassian.mail.Email");
                Object emailObj = emailClass.getConstructor(String.class).newInstance(to.trim());
                emailClass.getMethod("setSubject", String.class).invoke(emailObj, subject);
                emailClass.getMethod("setBody", String.class).invoke(emailObj, body);
                emailClass.getMethod("setMimeType", String.class).invoke(emailObj, "text/plain");
                if (!blank(settings.getSmtpFromAddress())) {
                    try { emailClass.getMethod("setFrom", String.class).invoke(emailObj, settings.getSmtpFromAddress()); } catch (Exception ignored) {}
                }
                smtp.getClass().getMethod("send", emailClass).invoke(smtp, emailObj);
                LOG.debug("TruffleHog direct-SMTP notification sent to {}", to);
            } finally {
                bc.ungetService(smRef);
            }
        } catch (Exception ex) {
            LOG.warn("TruffleHog: direct SMTP send failed to {}: {}", to, ex.getMessage());
        }
    }

    private void sendViaBitbucketMailServer(String to, String subject, String body) {
        try {
            Bundle bundle = FrameworkUtil.getBundle(getClass());
            if (bundle == null) { LOG.warn("TruffleHog: OSGi bundle context unavailable"); return; }
            BundleContext bc = bundle.getBundleContext();
            ServiceReference<?> smRef = bc.getServiceReference(MAIL_MGR_IFACE);
            if (smRef == null) { LOG.warn("TruffleHog: no MailServerManager OSGi service — skipping {}", to); return; }
            Object mailMgr = bc.getService(smRef);
            try {
                Method getSmtp = mailMgr.getClass().getMethod("getDefaultSMTPMailServer");
                Object smtp = getSmtp.invoke(mailMgr);
                if (smtp == null) { LOG.warn("TruffleHog: no SMTP server in Bitbucket — skipping {}", to); return; }
                ClassLoader smtpCl = smtp.getClass().getClassLoader();
                Class<?> emailClass = smtpCl.loadClass("com.atlassian.mail.Email");
                Object emailObj = emailClass.getConstructor(String.class).newInstance(to.trim());
                emailClass.getMethod("setSubject", String.class).invoke(emailObj, subject);
                emailClass.getMethod("setBody", String.class).invoke(emailObj, body);
                emailClass.getMethod("setMimeType", String.class).invoke(emailObj, "text/plain");
                smtp.getClass().getMethod("send", emailClass).invoke(smtp, emailObj);
                LOG.debug("TruffleHog notification sent to {}", to);
            } finally {
                bc.ungetService(smRef);
            }
        } catch (Exception ex) {
            LOG.warn("TruffleHog: failed to send notification to {}: {}", to, ex.getMessage(), ex);
        }
    }

    private boolean sendTeamsWebhook(String webhookUrl, String message) {
        String json = "{\"text\":\"" + escapeJson(message) + "\"}";
        return postWebhook(webhookUrl, json, "Teams");
    }

    private boolean sendSlackWebhook(String webhookUrl, String message) {
        String json = "{\"text\":\"" + escapeJson(message) + "\"}";
        return postWebhook(webhookUrl, json, "Slack");
    }

    private boolean postWebhook(String webhookUrl, String json, String label) {
        try {
            URL url = new URL(webhookUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(10000);
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }
            int status = conn.getResponseCode();
            if (status >= 200 && status < 300) {
                LOG.debug("TruffleHog {} webhook sent ok ({})", label, status);
                return true;
            }
            LOG.warn("TruffleHog {} webhook returned HTTP {}", label, status);
            return false;
        } catch (Exception ex) {
            LOG.warn("TruffleHog {} webhook failed: {}", label, ex.getMessage());
            return false;
        }
    }

    private String buildWebhookMessage(Repository repo, List<TruffleHogRawFinding> findings) {
        int verified = 0;
        for (TruffleHogRawFinding f : findings) { if (f.isVerified()) verified++; }
        return String.format("[TruffleHog] %d new secret(s) found in %s/%s (%d verified). Review in Bitbucket.",
                findings.size(), repo.getProject().getKey(), repo.getSlug(), verified);
    }

    private String interpolate(String template, Repository repo, TruffleHogRawFinding finding, int count) {
        if (template == null) return "";
        return template
                .replace("{{repository}}", repo.getSlug())
                .replace("{{project}}",    repo.getProject().getKey())
                .replace("{{detector}}",   nvl(finding.getDetectorName()))
                .replace("{{file}}",       nvl(finding.getFilePath()))
                .replace("{{branch}}",     nvl(finding.getBranch()))
                .replace("{{commit}}",     nvl(finding.getCommit()))
                .replace("{{count}}",      String.valueOf(count));
    }

    private String buildSummaryBody(Repository repo, List<TruffleHogRawFinding> findings) {
        StringBuilder sb = new StringBuilder();
        sb.append("TruffleHog detected ").append(findings.size()).append(" new secret(s) in ")
          .append(repo.getProject().getKey()).append("/").append(repo.getSlug()).append(":\n\n");
        int shown = Math.min(findings.size(), 20);
        for (int i = 0; i < shown; i++) {
            TruffleHogRawFinding f = findings.get(i);
            sb.append("  ").append(i + 1).append(". ")
              .append(nvl(f.getDetectorName()))
              .append(" | ").append(nvl(f.getFilePath()))
              .append(" | branch: ").append(nvl(f.getBranch()))
              .append(" | verified: ").append(f.isVerified())
              .append('\n');
        }
        if (findings.size() > shown) {
            sb.append("  ... and ").append(findings.size() - shown).append(" more.\n");
        }
        return sb.toString();
    }

    private List<String> splitRecipients(String raw) {
        List<String> result = new ArrayList<>();
        if (raw == null) return result;
        for (String addr : raw.split("[,;\\n\\r]+")) {
            String t = addr.trim();
            if (!t.isEmpty() && t.contains("@")) result.add(t);
        }
        return result;
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }

    private boolean blank(String s) { return s == null || s.trim().isEmpty(); }
    private String nvl(String s)    { return s == null ? "" : s; }
}
