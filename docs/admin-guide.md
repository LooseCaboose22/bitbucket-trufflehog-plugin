# TruffleHog Secret Scanner — Administrator Guide

**Version 1.5 | Bitbucket Data Center**

---

## Overview

This guide covers the installation, configuration, and ongoing administration of the TruffleHog Secret Scanner plugin for Bitbucket Data Center. The plugin integrates TruffleHog v3 into Bitbucket to provide automated secret detection on every push and scheduled scans of all repositories.

---

## Prerequisites

| Requirement | Details |
|---|---|
| Bitbucket Data Center | Version 8.x or later |
| TruffleHog binary | v3.x installed on every Bitbucket cluster node |
| Java | 11+ (provided by Bitbucket) |
| Network access | Outbound HTTPS if verification or webhooks are used |
| Permissions | System Administrator role to install and configure |

### Installing TruffleHog

On each Bitbucket cluster node:

```bash
# Linux (recommended)
curl -sSfL https://raw.githubusercontent.com/trufflesecurity/trufflehog/main/scripts/install.sh | sh -s -- -b /usr/local/bin

# Verify
trufflehog --version
```

Note the full path (e.g., `/usr/local/bin/trufflehog`) — you will need it during plugin configuration.

---

## Installation

### Via UPM (Universal Plugin Manager)

1. Log in to Bitbucket as a System Administrator
2. Navigate to **Administration → Manage apps**
3. Click **Upload app** and upload `trufflehog-plugin-<version>.jar`
4. Wait for the plugin to become **Enabled** (all 15 modules should show enabled)

### Verify Installation

```
GET /rest/plugins/1.0/com.acme.bitbucket.trufflehog-key
```

All 15 modules should have `"enabled": true, "broken": false`.

---

## Core Configuration

Navigate to **Administration → TruffleHog Secret Scanner → Settings**.

### Binary & Scan Settings

| Field | Description | Default |
|---|---|---|
| **Executable path** | Absolute path to the TruffleHog binary | `trufflehog` |
| **Repositories root** | Override Bitbucket's default repo directory | *(Bitbucket default)* |
| **Extra CLI args** | Extra flags passed verbatim to TruffleHog | *(empty)* |
| **Allowlist regex** | Secrets whose redacted value matches are silently skipped | *(empty)* |
| **Cron expression** | Quartz cron for scheduled full scans | `0 0 2 * * ?` (2 AM daily) |
| **Concurrent scans** | Parallel repository scans during full scan | `4` |
| **Scan timeout (min)** | Kill a scan if it exceeds this duration | `30` |

### Detection Filters

| Field | Description |
|---|---|
| **Verification filter** | `Both` (recommended), `Verified only`, or `Unverified only` |
| **Detector allowlist** | Only report these detector types (leave blank = all) |
| **Detector ignorelist** | Never report these detector types |

### Scope

| Field | Description |
|---|---|
| **Enable globally** | Scan all repositories (recommended). Uncheck for explicit allowlist. |
| **Enabled projects** | Project keys to scan when global mode is off |
| **Enabled repositories** | `PROJECT/repo-slug` pairs to scan when global mode is off |
| **Excluded projects** | Project keys to always skip |
| **Excluded repositories** | `PROJECT/repo-slug` pairs to always skip |

---

## Push Behavior (v1.2)

Configure how TruffleHog responds to pushed commits.

### Push Result Mode

| Mode | Behavior |
|---|---|
| **Block** | Rejects pushes that introduce secrets on matching branches |
| **Warn** | Allows pushes but logs findings and sends notifications |
| **Silent** | Scans in background; no notification or blocking |

### Branch Patterns

- **Block branch patterns** — branches where BLOCK mode applies (e.g., `main,master,release/*`)
- **Warn branch patterns** — branches that downgrade from BLOCK to WARN (e.g., `feature/*,dev`)

Patterns support exact names and `*` glob. Comma-separated.

### Excluded Pushers

Comma-separated Bitbucket usernames whose pushes skip scanning entirely. Use for CI service accounts (e.g., `jenkins-bot,deploy-user`).

### Size Limits

| Field | Description |
|---|---|
| **Max file size (KB)** | Skip individual files larger than this. `0` = no limit. |
| **Max total scan size (MB)** | Abort a repo scan if total data exceeds this. `0` = no limit. |

---

## Email Notifications (v1.1)

### Option A: Bitbucket Mail Server

Leave all direct SMTP fields blank. The plugin uses the SMTP server configured in **Administration → Mail server**.

### Option B: Direct SMTP Override

Fill in the SMTP fields to bypass Bitbucket's mail server:

| Field | Description |
|---|---|
| **SMTP host** | Hostname or IP (e.g., `smtp.office365.com`) |
| **SMTP port** | Usually `587` (STARTTLS) or `465` (SSL) |
| **Use TLS** | Enable STARTTLS (recommended for port 587) |
| **Username / Password** | SMTP credentials |
| **From address** | Override the From header (e.g., `trufflehog@company.com`) |

Use the **Send test** button to verify the configuration before saving.

### Notification Recipients

| Section | Field | Description |
|---|---|---|
| Committer | *Notify committer* | Email the commit author when their commit introduces a secret |
| Admin | *Admin recipients* | Aggregated summary after each scan with new findings |
| Infosec | *Infosec recipients* | Same summary, separate list for the security team |

Templates support variables: `{{repository}}`, `{{project}}`, `{{detector}}`, `{{file}}`, `{{branch}}`, `{{commit}}`, `{{count}}`.

### Notification Mode

| Mode | Behavior |
|---|---|
| **Immediate** | Alert fires after every scan that produces new findings |
| **Daily digest** | Findings batched and sent once per day at the configured hour (UTC) |

---

## Webhook Notifications (v1.1)

### Microsoft Teams

1. In Teams, add an **Incoming Webhook** connector to your channel
2. Copy the webhook URL
3. Paste it into **Teams webhook URL** in the settings
4. Use **Test** to verify

### Slack

1. Create a Slack app with an incoming webhook at `api.slack.com`
2. Copy the webhook URL
3. Paste it into **Slack webhook URL** in the settings
4. Use **Test** to verify

Webhook payload format: `{"text": "[TruffleHog] N new secret(s) found in PROJECT/repo (V verified)."}`.

---

## Custom Detectors (v1.4)

### Warn-Only Detectors

Findings from these detectors are flagged as warnings and never block pushes, even in BLOCK mode. Useful for detectors with high false-positive rates (e.g., `Entropy`, `Generic`).

Enter detector names comma-separated (e.g., `Entropy, Generic`).

### Custom Detector Config Path

Supply an absolute path to a TruffleHog `--config` YAML file on the Bitbucket node(s). This allows you to add custom regex detectors or modify built-in ones.

Example YAML structure:
```yaml
detectors:
  - name: InternalApiKey
    keywords:
      - "INTERNAL-"
    regex:
      secret: "INTERNAL-[0-9A-F]{32}"
    verify:
      - endpoint: https://api.internal.example.com/verify
```

---

## Proxy Settings (v1.5)

TruffleHog makes outbound HTTPS calls to verify secrets against live services. If your Bitbucket nodes are behind a proxy:

| Field | Description |
|---|---|
| **HTTP/HTTPS proxy URL** | e.g., `http://proxy.corp.local:3128` |
| **No-proxy hosts** | Comma-separated hosts/CIDRs that bypass the proxy |
| **Proxy username / Password** | For authenticated proxies |
| **Custom SSL certificate** | PEM/CRT path for a self-signed CA (sets `SSL_CERT_FILE`) |

These settings are injected into the TruffleHog process environment, not the JVM.

---

## Data Center: Per-Node Binary Paths (v1.5)

In clustered deployments where TruffleHog is installed at different paths per node:

```
node1=/usr/local/bin/trufflehog
node2=/opt/trufflehog/bin/trufflehog
```

The node name must match Bitbucket's cluster node ID (visible in **Administration → Clustering**). Nodes without an entry use the global **Executable path**.

---

## File Exclusions

| Setting | Description |
|---|---|
| **Excluded extensions** | Skip files with these extensions (e.g., `.class,.jar,.png`) |
| **Excluded directories** | Skip path prefixes (e.g., `vendor/, node_modules/`) |
| **Allowlisted paths** | Force-scan these paths even if matched by exclusions |

---

## Per-Secret Allowlist

Permanently suppress a specific finding by adding its **fingerprint** (SHA-256 hash) to the allowlist.

1. Open the finding in the dashboard
2. Copy the fingerprint
3. Go to **Settings → Per-secret allowlist**
4. Add the fingerprint with a reason

Allowlisted findings are invisible in all dashboards and do not trigger notifications. They can be removed at any time.

---

## Config Export / Import (v1.5)

### Export

Click **Download trufflehog-config.json** to download the current settings as JSON. Passwords are omitted from the export for security.

### Import

1. Select a previously exported JSON file
2. Click **Import**
3. The import preserves SMTP and proxy passwords if they are absent in the file

Use export/import to replicate settings across environments (dev → staging → prod) or to back up your configuration before upgrades.

---

## Audit Log (v1.4)

The audit log records all administrative actions:

| Action | Description |
|---|---|
| `PUSH_SCAN` | Post-receive hook triggered a scan |
| `TEST_EMAIL` | Test email was sent via the admin UI |
| `TEST_TEAMS` | Test Teams webhook was triggered |
| `TEST_SLACK` | Test Slack webhook was triggered |
| `CONFIG_IMPORT` | Settings were imported via REST |

The log retains the 500 most recent entries. It is visible in **Settings → Audit log** and via:

```
GET /rest/th-scanner/1.0/audit?limit=100
```

---

## REST API Reference

Base URL: `/rest/th-scanner/1.0/`

| Method | Path | Description |
|---|---|---|
| `GET` | `settings` | Retrieve current settings |
| `PUT` | `settings` | Update settings |
| `GET` | `findings` | List findings (supports filters, pagination) |
| `PUT` | `findings/{id}/status` | Update finding status |
| `POST` | `scan/all` | Trigger full system scan |
| `POST` | `scan/project/{key}` | Trigger project scan |
| `POST` | `scan/repository/{key}/{slug}` | Trigger repository scan |
| `GET` | `dashboard/{scope}` | Get dashboard data |
| `GET` | `allowlist` | List allowlisted fingerprints |
| `POST` | `allowlist` | Add allowlist entry |
| `DELETE` | `allowlist/{id}` | Remove allowlist entry |
| `GET` | `audit` | List audit log entries |
| `POST` | `notifications/test` | Send a test notification |
| `GET` | `config/export` | Export settings as JSON |
| `POST` | `config/import` | Import settings from JSON |

All endpoints require **System Administrator** permission except finding reads (repository read permission).

---

## Troubleshooting

### Plugin modules not enabled

Check `atlassian-bitbucket.log` for `ao-module` errors. The most common cause is an AO table name exceeding 30 characters. Verify the `@Table` annotations in the AO entity classes.

### TruffleHog binary not found

- Verify the path is correct and executable: `which trufflehog` on the Bitbucket node
- In Data Center, each node must have the binary at the configured path (or use per-node paths)
- Check file permissions: the Bitbucket service account must be able to execute it

### Scans produce no findings

- Check `verifiedFilter` — if set to `VERIFIED_ONLY`, unverified secrets are dropped
- Check that the repository is in scope (not excluded)
- Check `scanTimeoutMinutes` — scans that timeout return partial results

### Email not delivered

- Use **Send test** from the Settings page
- Check Bitbucket's mail server configuration if not using direct SMTP
- Check spam/junk folders

### Push hook not firing

- Verify the hook is enabled on the repository/project in **Repository Settings → Hooks**
- Check `scanOnPush` is enabled in plugin settings

---

*For developer information, see the [User Guide](user-guide.md).*
*For operational procedures, see the [Runbook](runbook.md).*
