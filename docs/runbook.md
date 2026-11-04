# TruffleHog Secret Scanner — Operational Runbook

**Version 1.5 | Bitbucket Data Center**

---

## Overview

This runbook covers day-to-day operations, incident response, and maintenance procedures for the TruffleHog Secret Scanner plugin running on Bitbucket Data Center.

---

## Key Locations

| Resource | Location |
|---|---|
| Plugin JAR | UPM → Manage apps |
| Configuration UI | Administration → TruffleHog Secret Scanner → Settings |
| REST API | `https://<bitbucket>/rest/th-scanner/1.0/` |
| Application log | `<bitbucket-home>/log/atlassian-bitbucket.log` |
| TruffleHog binary | `/usr/local/bin/trufflehog` (default; may vary per node) |
| AO database | Bitbucket's embedded/external DB, tables prefixed `AO_501204_` |

### AO Table Reference

| Table | Purpose |
|---|---|
| `AO_501204_AO_SECRET_FINDING` | Secret findings |
| `AO_501204_SCAN_RUN` | Scan run history |
| `AO_501204_AO_SCAN_CONFIG` | Plugin configuration store |
| `AO_501204_REMEDIATION_EVENT` | Finding status change history |
| `AO_501204_SECRET_ALLOWLIST` | Per-secret allowlist entries |
| `AO_501204_AUDIT_LOG` | Audit log entries |

---

## Health Checks

### 1. Plugin status

```bash
curl -u admin:password https://bitbucket.example.com/rest/plugins/1.0/com.acme.bitbucket.trufflehog-key \
  | jq '{enabled, broken, version}'
```

Expected: `"enabled": true, "broken": false`.

### 2. All modules enabled

```bash
curl -u admin:password https://bitbucket.example.com/rest/plugins/1.0/com.acme.bitbucket.trufflehog-key/modules \
  | jq '[.modules[] | {key, enabled, broken}]'
```

Expected: all 15 modules `enabled: true, broken: false`.

### 3. REST API responding

```bash
curl -u admin:password https://bitbucket.example.com/rest/th-scanner/1.0/settings
```

Expected: HTTP 200 with JSON settings.

### 4. TruffleHog binary reachable

```bash
# On each Bitbucket node:
sudo -u bitbucket /usr/local/bin/trufflehog --version
```

Expected: `trufflehog 3.x.x`.

### 5. Recent scan activity

```bash
curl -u admin:password 'https://bitbucket.example.com/rest/th-scanner/1.0/dashboard/system' \
  | jq '.scanRuns[0]'
```

Check `lastScanAt` is recent (within the cron window).

---

## Common Operational Tasks

### Trigger a manual full scan

```bash
curl -X POST -u admin:password \
  https://bitbucket.example.com/rest/th-scanner/1.0/scan/all
```

Response: `{"status": "queued"}` (HTTP 202). Monitor the log for progress.

### Trigger a scan for one repository

```bash
curl -X POST -u admin:password \
  https://bitbucket.example.com/rest/th-scanner/1.0/scan/repository/PROJ/my-repo
```

### Check finding counts

```bash
curl -u admin:password \
  'https://bitbucket.example.com/rest/th-scanner/1.0/findings?status=OPEN&limit=1' \
  | jq '.total'
```

### Export settings backup

```bash
curl -u admin:password \
  https://bitbucket.example.com/rest/th-scanner/1.0/config/export \
  -o trufflehog-config-backup-$(date +%Y%m%d).json
```

### Import settings from backup

```bash
curl -X POST -u admin:password \
  -H 'Content-Type: application/json' \
  -d @trufflehog-config-backup-20241101.json \
  https://bitbucket.example.com/rest/th-scanner/1.0/config/import
```

### Add a finding to the allowlist

```bash
curl -X POST -u admin:password \
  -H 'Content-Type: application/json' \
  -d '{"fingerprint":"<sha256>","detectorName":"AWS","reason":"Test credential"}' \
  https://bitbucket.example.com/rest/th-scanner/1.0/allowlist
```

### View audit log

```bash
curl -u admin:password \
  'https://bitbucket.example.com/rest/th-scanner/1.0/audit?limit=50' \
  | jq '.[] | {action, actor, entityId, occurredAt}'
```

---

## Incident Response

### INC-1: Plugin broken / modules not enabled

**Symptoms:** UPM shows modules as broken; `/rest/plugins/1.0/...` returns `broken: true`.

**Investigation:**
```bash
grep -i "trufflehog\|AO_501204\|ao-module" /path/to/atlassian-bitbucket.log | tail -100
```

**Common causes and fixes:**

| Cause | Fix |
|---|---|
| AO table name too long (>30 chars) | Verify all `@Table` annotations; the prefix `AO_501204_` is 10 chars, leaving 20 for the table name |
| Missing OSGi dependency | Check `spring-scanner` annotations; re-upload the JAR |
| Database migration failure | Check AO migration logs; may need to drop and recreate AO tables (data loss) |

**Recovery:**
1. Disable and re-enable the plugin in UPM
2. If that fails, restart Bitbucket
3. If still broken, re-upload the JAR

---

### INC-2: Post-receive hook not scanning

**Symptoms:** Pushes complete without scans; no new findings after push.

**Checklist:**
- [ ] `scanOnPush` is `true` in settings
- [ ] Hook is enabled on the repository/project (Repository Settings → Hooks)
- [ ] Push mode is not `SILENT`
- [ ] Pusher username is not in `excludedPusherIds`
- [ ] Repository is in scope (not excluded by project/repo exclusion lists)

**Log check:**
```bash
grep "PostReceiveScanHook\|post-receive" /path/to/atlassian-bitbucket.log | tail -50
```

---

### INC-3: TruffleHog scans timing out

**Symptoms:** Scan runs show as timed out; `scanTimeoutMinutes` exceeded.

**Immediate action:** Increase `scanTimeoutMinutes` for affected repositories.

**Root causes:**
- Large repositories (> 1 GB): set `maxFileSizeKb` and `maxTotalSizeMb` limits
- Network-dependent verification: check if `--only-verified` (in extra args) is causing timeouts — remove it or increase timeout
- Slow node: check CPU/memory on the node running the scan

**Monitoring:**
```bash
grep "timed out\|killed\|destroyForcibly" /path/to/atlassian-bitbucket.log | tail -20
```

---

### INC-4: No email notifications delivered

**Symptoms:** Scans complete with new findings, no emails sent.

**Checklist:**
- [ ] `notifyAdmins` or `notifyInfosec` is enabled
- [ ] Recipients are correctly formatted (valid email addresses)
- [ ] SMTP server is configured (either in plugin settings or Bitbucket's mail server)
- [ ] Use **Send test** from the Settings page and check the result

**Direct SMTP test:**
```bash
curl -X POST -u admin:password \
  -H 'Content-Type: application/json' \
  -d '{"type":"email","to":"your@email.com"}' \
  https://bitbucket.example.com/rest/th-scanner/1.0/notifications/test
```

**Log check:**
```bash
grep "NotificationService\|sendEmail\|SMTP" /path/to/atlassian-bitbucket.log | tail -30
```

---

### INC-5: Webhook notifications failing

**Test webhooks:**
```bash
# Teams
curl -X POST -u admin:password \
  -H 'Content-Type: application/json' \
  -d '{"type":"teams"}' \
  https://bitbucket.example.com/rest/th-scanner/1.0/notifications/test

# Slack
curl -X POST -u admin:password \
  -H 'Content-Type: application/json' \
  -d '{"type":"slack"}' \
  https://bitbucket.example.com/rest/th-scanner/1.0/notifications/test
```

**Log check:**
```bash
grep "webhook\|Teams\|Slack" /path/to/atlassian-bitbucket.log | tail -30
```

**Common issues:**
- Webhook URL changed — re-verify in Teams/Slack and update settings
- Bitbucket nodes cannot reach the webhook endpoint — check firewall/proxy settings
- Rate limiting by Teams/Slack — reduce notification frequency

---

### INC-6: False positive storm (many incorrect findings)

**Symptoms:** Large number of low-confidence findings overwhelming the dashboard.

**Immediate mitigations:**
1. Add high-noise detectors to **Warn-only detectors** (e.g., `Entropy`, `Generic`)
2. Set `verifiedFilter` to `VERIFIED_ONLY` temporarily
3. Add noisy file extensions to **Excluded extensions**
4. Add noisy path prefixes to **Excluded directories**

**Long-term:**
- Add a custom detector config with more precise regex patterns
- Add specific file patterns to the allowlisted paths

---

### INC-7: TruffleHog binary update

**When:** A new TruffleHog version is available or a security advisory is issued.

**Procedure:**
1. Download the new binary to a staging location
2. Test on one non-production node: `trufflehog --version`
3. Update on all production nodes sequentially
4. Update the **Executable path** in plugin settings if the path changed
5. Trigger a manual scan to verify the new version works
6. Record the update in the audit log (add via REST)

---

## Maintenance Procedures

### Scheduled maintenance window

Before starting:
```bash
# Backup settings
curl -u admin:password https://bitbucket.example.com/rest/th-scanner/1.0/config/export \
  -o backup-pre-maintenance-$(date +%Y%m%d).json
```

After completing:
```bash
# Verify plugin still healthy
curl -u admin:password https://bitbucket.example.com/rest/plugins/1.0/com.acme.bitbucket.trufflehog-key \
  | jq '{enabled, broken}'
# Run a test scan
curl -X POST -u admin:password \
  https://bitbucket.example.com/rest/th-scanner/1.0/scan/repository/TEST/test-repo
```

### Plugin upgrade

1. Export current settings: `GET /rest/th-scanner/1.0/config/export`
2. Upload new JAR via UPM
3. Wait for all modules to show enabled
4. Verify settings were preserved: `GET /rest/th-scanner/1.0/settings`
5. If settings were lost, import the backup: `POST /rest/th-scanner/1.0/config/import`
6. Trigger a test scan

### Database cleanup (findings retention)

There is no built-in retention policy; findings accumulate indefinitely. To clean up old findings:

```sql
-- Preview count of old closed findings
SELECT COUNT(*) FROM "AO_501204_AO_SECRET_FINDING"
WHERE "STATUS" != 'OPEN'
  AND "RESOLVED_AT" < EXTRACT(EPOCH FROM NOW() - INTERVAL '365 days') * 1000;

-- Delete old closed findings (adjust date as needed)
DELETE FROM "AO_501204_AO_SECRET_FINDING"
WHERE "STATUS" != 'OPEN'
  AND "RESOLVED_AT" < EXTRACT(EPOCH FROM NOW() - INTERVAL '365 days') * 1000;
```

Always take a database backup before running DML.

### Audit log retention

The audit log is capped at 500 entries in memory but persists all entries in the AO table. To purge old entries:

```sql
DELETE FROM "AO_501204_AUDIT_LOG"
WHERE "OCCURRED_AT" < EXTRACT(EPOCH FROM NOW() - INTERVAL '90 days') * 1000;
```

---

## Monitoring Recommendations

### Log-based alerts (Splunk / ELK / Graylog)

| Query | Alert condition |
|---|---|
| `"TruffleHog timed out"` | > 3 in 1 hour |
| `"PostReceiveScanHook" "failed"` | Any occurrence |
| `"ao-module" "broken"` | Any occurrence |
| `"Push scan skipped"` | > 50 in 1 hour (may indicate excluded pusher config drift) |

### Metrics to track

- **Findings opened per week** — baseline for normal developer activity
- **Scan duration p95** — alert if > `scanTimeoutMinutes * 0.8`
- **Verified finding rate** — high rate indicates real credentials committed

---

## Contact & Escalation

| Issue | Owner |
|---|---|
| Plugin configuration | Bitbucket System Admin team |
| Secret findings — critical (verified, production) | Security / CISO team |
| TruffleHog binary issues | DevOps / Infrastructure team |
| Plugin bugs / feature requests | Plugin maintainer (file via project issue tracker) |

---

*For developer information, see the [User Guide](user-guide.md).*
*For configuration details, see the [Admin Guide](admin-guide.md).*
