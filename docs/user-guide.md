# TruffleHog Secret Scanner — User Guide

**Version 1.5 | Bitbucket Data Center**

---

## Overview

The TruffleHog Secret Scanner plugin automatically detects leaked secrets (API keys, passwords, tokens, certificates, and other credentials) in your Bitbucket repositories. It uses [TruffleHog v3](https://github.com/trufflesecurity/trufflehog) — the industry-standard open-source secret detection engine — integrated directly into Bitbucket Data Center.

### What counts as a "secret"?

TruffleHog detects over 700 types of credentials, including:

| Category | Examples |
|---|---|
| Cloud providers | AWS access keys, Azure SAS tokens, GCP service account keys |
| Source control | GitHub personal access tokens, GitLab tokens |
| Payment systems | Stripe API keys, PayPal client secrets |
| Communication | Slack tokens, Twilio API keys |
| Databases | MongoDB connection strings, Postgres passwords |
| Generic patterns | Private keys (RSA/EC/PGP), JWT secrets, Base64-encoded tokens |

---

## For Developers

### Push Behavior

Depending on the administrator's configuration, when you push commits containing secrets you will see one of three responses:

#### BLOCK mode
Your push is **rejected** if it introduces a verified secret on a protected branch (typically `main` or `master`). You will see an error in your git client:

```
remote: [TruffleHog] Push rejected — verified secret detected.
remote: Detector: AWS, File: config/app.properties, Branch: main
remote: Remediate the secret before pushing to protected branches.
```

**What to do:**
1. Rotate the secret immediately (even if it's a test key)
2. Remove it from your commit history using `git filter-repo` or `BFG Repo-Cleaner`
3. Re-push after cleaning the history

#### WARN mode
Your push **succeeds** but a warning is logged and notifications are sent. You may receive an email from the security team.

#### SILENT mode
Scans run in the background. You will not be notified directly, but findings appear in the dashboards.

---

### Viewing Findings

Findings for a repository are visible on the **Repository Dashboard**:

1. Navigate to your repository in Bitbucket
2. Click **TruffleHog** in the left navigation
3. View open findings, their severity, and remediation status

Each finding shows:
- **Detector** — which type of secret was found (e.g., `AWS`, `Stripe`)
- **File path and line number** — where in the code it appears
- **Branch and commit** — exactly which commit introduced it
- **Verified** — whether TruffleHog confirmed the credential is still active against the live service

### Finding Statuses

| Status | Meaning |
|---|---|
| **Open** | New finding, not yet reviewed |
| **Fixed** | Secret has been rotated and removed from code |
| **False positive** | Not actually a secret (e.g., a test value) |
| **Accepted risk** | Known, documented risk that is intentionally tolerated |

To update a finding's status, click it in the dashboard and select the new status. A reason of at least 8 characters is required for non-Open statuses.

---

### FAQ for Developers

**Q: I committed a test API key that's not real. What should I do?**

Mark the finding as **False positive** with a note explaining it's a test value. If the pattern is always safe (e.g., `test_key_`, `example.com`), ask your admin to add it to the allowlist regex.

**Q: My commit was blocked but the secret is in a dependency lock file, not my code.**

Add the path prefix (e.g., `vendor/`, `node_modules/`) to the excluded directories in the admin settings, or ask your admin to do so.

**Q: I rotated the secret. Now what?**

1. Remove or replace the secret in the codebase
2. Mark the finding as **Fixed** with a note
3. If it was in git history, clean the history with `git filter-repo`

**Q: I pushed to a feature branch and it wasn't blocked — why?**

The admin may have configured BLOCK mode to apply only to specific branches (e.g., `main`, `master`). Feature branches may be in WARN mode instead.

**Q: Will TruffleHog scan my pull request?**

The post-receive hook fires after the push. It scans the pushed commits, not the PR diff itself.

---

## Dashboard Navigation

### Repository Dashboard

Shows findings scoped to a single repository. Accessible from the repository's left navigation menu.

- **Summary bar** — count of open/fixed/false-positive findings
- **Findings table** — sortable by severity, status, detector, last seen
- **Scan history** — recent scan runs, duration, findings count

### Project Dashboard

Shows an aggregate view across all repositories in a project. Accessible from the project's navigation.

- **Top repositories** — ranked by open finding count
- **Detector distribution** — which secret types are most common in your project

### System Dashboard

A global view for administrators. Shows:

- All findings across all projects
- Project-level rollup
- Recent scan runs system-wide
- A scan outcomes donut chart (New / Recurring / Resolved)
- Top detectors by finding count

---

## Notifications

### Email

You may receive an email notification when a secret is detected in a commit you authored. The email includes the repository, file path, branch, and detector type.

### Teams / Slack

Your administrator may have configured webhook alerts to a shared channel. These notifications include the repository, number of new secrets, and a count of verified secrets.

---

## Allowlist: Suppressing Known False Positives

If a specific secret fingerprint is a known safe value (e.g., a published example key, a test credential committed intentionally), an administrator can add its fingerprint to the **per-secret allowlist**. Once allowlisted, the finding is permanently suppressed and will not appear in dashboards.

To request an allowlist entry, provide your admin with:
- The **fingerprint** (SHA-256 hash, visible on the finding detail page)
- The **detector name**
- The **reason** (e.g., "Published example key in docs, not a real credential")

---

*For administrator configuration, see the [Admin Guide](admin-guide.md).*
*For operational runbook, see the [Runbook](runbook.md).*
