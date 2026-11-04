# TruffleHog Secret Scanner — Documentation

Bitbucket Data Center plugin that integrates [TruffleHog v3](https://github.com/trufflesecurity/trufflehog) into your repository lifecycle: scheduled scans, push-triggered scans, and a browser-based dashboard for triaging and remediating exposed secrets.

---

## Documents

| Guide | Audience | What it covers |
|-------|----------|----------------|
| [User Guide](user-guide.md) | Developers, security engineers | Navigating dashboards, understanding findings, remediation workflow |
| [Admin Guide](admin-guide.md) | Bitbucket administrators | Installation, configuration, permissions, operations |
| [Architecture Guide](architecture.md) | Plugin developers, maintainers | Code structure, data model, API, design decisions |

---

## Quick facts

- **Bitbucket Data Center** 8.x (tested on 8.19.29)
- **Java** 11+
- **TruffleHog** v3 binary required on each cluster node
- REST API at `/rest/trufflehog/1.0/`
- Dashboards at `/plugins/servlet/trufflehog/dashboard/{system,project,repo}`
- Settings at `/plugins/servlet/trufflehog/admin`
- Findings are **deduplicated by fingerprint** — the same secret in multiple commits counts as one finding
- Secrets are **never stored** — only the redacted (masked) value is persisted

---

## Support

Report bugs and feature requests in the project issue tracker.
