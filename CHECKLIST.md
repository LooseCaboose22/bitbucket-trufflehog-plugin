# TruffleHog Plugin — Settings Feature Checklist

Status: `[x]` = done, `[ ]` = not yet implemented

---

## TruffleHog Binary
- [x] Executable path (absolute path or command on PATH)
- [x] Repositories root override
- [x] Extra CLI args (passed verbatim)
- [ ] Per-node binary path (Data Center — one path per cluster node)
- [ ] Test binary on all nodes button

## Scheduling & Scan Limits
- [x] Cron expression for scheduled full scans
- [x] Max concurrent scans (parallelism during full scan)
- [x] Scan timeout (minutes per repository)
- [ ] Max file size (KB) — skip files larger than this
- [ ] Max total extracted size (MB) — abort scan if archive extraction exceeds this
- [ ] Redact secret values in output (replace raw value with `***`)
- [ ] Temp directory cleanup — enable automatic cleanup
- [ ] Temp directory cleanup — cleanup interval (minutes)
- [ ] Temp directory cleanup — max directory age (seconds)

## Scope — Which Repos Are Scanned
- [ ] Enable globally for all repositories toggle
- [ ] Enabled Projects allowlist (only scan these project keys)
- [ ] Enabled Repositories allowlist (only scan these repo slugs)
- [ ] Excluded Projects exceptions (skip even if globally enabled)
- [ ] Excluded Repositories exceptions (skip even if globally enabled)
- [ ] Excluded Pusher IDs — skip scan when these users push
- [ ] Silent mode for excluded pushers (no warning shown to user)

## Detection Filters
- [x] Verified / unverified filter (Both / Verified only / Unverified only)
- [x] Detector allowlist — only report findings from listed detectors
- [x] Detector ignorelist — never report findings from listed detectors
- [ ] Warn-only detectors — report but never block for these detectors
- [x] Allowlist regex — skip findings whose redacted value matches

## File Exclusions
- [ ] Excluded extensions (e.g. `.class`, `.jar`, `.dll`, `.exe`)
- [ ] Excluded directories (e.g. `vendor/`, `.git/cache/`)
- [ ] Allowlisted paths — always scan these even if otherwise excluded
- [ ] Enable glob pattern matching for allowlisted paths

## Push / PR Behavior
- [x] Scan on push toggle
- [x] Block push on verified finding (pre-receive hook)
- [ ] Push result mode — Block / Warn-only / Silent monitoring
- [ ] Full Request Merge Action — Allow / Warn / Block
- [ ] Block branch patterns — force block on matching branches
- [ ] Warn-only branch patterns — warn but allow on matching branches
- [ ] Full-cloned mode (scan full repo) vs incremental (scan pushed commits only)

## Proxy Settings
- [ ] HTTP Proxy URL
- [ ] HTTPS Proxy URL
- [ ] No Proxy hosts (comma-separated)
- [ ] Use proxy for outbound TruffleHog verification calls
- [ ] Proxy username / password
- [ ] SSL Certificate File path (PEM)
- [ ] SSL Certificate Directory path

## Custom Regex Detectors
- [ ] External config file path (YAML/JSON loaded at scan time)
- [ ] Inline custom detector definitions (name, regex pattern, test input)
- [ ] Add / edit / delete custom detectors via UI

## Per-Secret Allowlist
- [ ] Fingerprint-based allowlist — permanently ignore a specific known secret
- [ ] Compute fingerprint from raw secret (UI helper tool)
- [ ] View / remove secrets from allowlist

## Notifications — Email
- [x] Notify committer (email the Git commit author)
- [x] Committer email subject / body templates
- [x] Notify admins — aggregated summary
- [x] Admin recipients list
- [x] Admin email subject template
- [x] Notify infosec team — aggregated summary
- [x] Infosec recipients list
- [x] Infosec email subject template
- [ ] Direct SMTP configuration (host, port, from address, user, password)
- [ ] STARTTLS encryption toggle
- [ ] Notification mode — Immediate (every event) vs Digest
- [ ] Digest send hour
- [ ] Send Test Email button
- [ ] Send Digest Now button

## Notifications — Other Channels
- [ ] Microsoft Teams webhook URL
- [ ] Help / Remediation URL (linked in notification messages)

## Audit & Metrics
- [ ] Audit log — config changes, scan events, allowed notifications
- [ ] Scan Metrics Dashboard — scan outcomes donut chart
- [ ] Scan Metrics Dashboard — top detectors by finding count (bar chart)
- [ ] Scan Metrics Dashboard — top repositories by findings
- [ ] Scan Metrics Dashboard — recent activity (bar chart over time)
- [ ] Scan Metrics Dashboard — per-node health status table
- [ ] Recent Scan Events table (timestamp, repo, branch, secrets, outcome)
- [ ] Export scan data as CSV
- [ ] Export / Import plugin configuration

## Infrastructure (Data Center)
- [ ] Plugin Health Status — all nodes view (version, binary path, status per node)
- [ ] Per-node TruffleHog binary path override
- [ ] Refresh node data button
