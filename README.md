# TruffleHog Secret Scanner for Bitbucket Data Center

An Atlassian Bitbucket Data Center plugin that wraps [TruffleHog OSS](https://github.com/trufflesecurity/trufflehog) to find leaked credentials across every repository, with system / project / repository dashboards and a remediation workflow.

## What it does

- **Scans every repo, on a schedule and on every push.** A daily full scan runs cluster-safe via the Atlassian scheduler. A post-receive hook triggers an incremental scan whenever a push lands.
- **Dashboards at three scopes.** System-wide (all projects), project (single project), and repository (single repo) views with the same shell — KPI cards, severity donut, top-detectors bar chart, open-findings trend, top affected repos, recent scans, filterable findings table, and a detail drawer.
- **Remediation workflow.** Findings flow through `OPEN → FIXED | FALSE_POSITIVE | ACCEPTED_RISK`. Every status change is recorded as an audit event with actor, timestamp, and a free-form note.
- **Cluster-safe persistence.** Findings, scan runs, configuration and remediation events are stored via Atlassian Active Objects, so the plugin works correctly on multi-node Bitbucket DC.

## Requirements

- Bitbucket Data Center 8.x (tested against 8.19)
- Java 11
- Maven 3.8+ (for building)
- TruffleHog v3 binary on every Bitbucket node's `PATH` (or absolute path configured via plugin settings)

## Build

```bash
mvn -B clean package
```

The plugin JAR lands in `target/trufflehog-secret-scanner-1.0.0.jar`.

## Install

1. Build the JAR (above) or grab it from a release artifact.
2. In Bitbucket, go to **Administration → Manage apps → Upload app** and select the JAR.
3. Confirm the **TruffleHog Secret Scanner** plugin shows as Enabled.
4. Install the TruffleHog v3 binary on every Bitbucket node (`brew install trufflehog`, `apt`, or [from releases](https://github.com/trufflesecurity/trufflehog/releases)).
5. Go to **Administration → Secret Scanner → Secret Scanner Settings** and verify the binary path, cron, etc.

## Configuration

Settings live in plugin settings (per cluster) and are edited via the admin servlet or the REST API:

| Field | Default | Meaning |
| --- | --- | --- |
| `trufflehogExecutable` | `trufflehog` | Absolute path or PATH-resolved binary |
| `repositoriesRoot` | (empty — auto) | Override for the Bitbucket repositories directory |
| `extraArgs` | `--only-verified` | Passed verbatim to `trufflehog` |
| `cronExpression` | `0 0 2 * * ?` | Quartz cron expression for the daily full scan |
| `maxConcurrentScans` | `2` | Parallelism during a full scan |
| `scanTimeoutMinutes` | `30` | Per-repo timeout — process is killed after this |
| `scanOnPush` | `true` | Run a scan after every successful push |
| `blockPushOnVerifiedFinding` | `false` | Reserved for a future pre-receive hook |
| `allowlistRegex` | (empty) | Skip findings whose redacted value matches |

REST:

```http
GET  /rest/trufflehog/1.0/settings
PUT  /rest/trufflehog/1.0/settings
```

## REST API

All endpoints are namespaced under `/rest/trufflehog/1.0/`.

### Findings

- `GET  /findings?projectKey=&repositorySlug=&status=&detectorType=&onlyVerified=&limit=&offset=` — list findings
- `GET  /findings/{id}` — finding detail + remediation history
- `GET  /findings/{id}/history` — remediation history only
- `PUT  /findings/{id}/status` — body `{ "status": "FIXED|FALSE_POSITIVE|ACCEPTED_RISK|OPEN", "remediationNote": "..." }`

### Scans

- `POST /scan/all` — trigger a full cluster scan (SYS_ADMIN)
- `POST /scan/project/{projectKey}` — trigger a project-wide scan (PROJECT_ADMIN)
- `POST /scan/repository/{projectKey}/{repositorySlug}` — trigger a single-repo scan (REPO_ADMIN)

### Dashboards

- `GET  /dashboard/system`
- `GET  /dashboard/project?projectKey=ABC`
- `GET  /dashboard/repository?projectKey=ABC&repositorySlug=my-repo`

Each returns a `summary` (counts + breakdowns), `scanRuns`, and rollups appropriate to the scope.

## Permission model

| Action | Required permission |
| --- | --- |
| View system dashboard | Global ADMIN |
| Modify settings, trigger full scan | Global SYS_ADMIN |
| View project dashboard | PROJECT_READ |
| Trigger project scan / remediate at project scope | PROJECT_ADMIN |
| View repository dashboard | REPO_READ |
| Trigger repository scan / remediate finding | REPO_ADMIN |

Permission checks happen in `PluginPermissionService` and apply to both the REST API and the dashboard servlets.

## Architecture

```
.
├── ao/                            Active Objects entities (cluster-safe persistence)
│   ├── AoSecretFinding            One row per (repoId, fingerprint)
│   ├── AoScanRun                  One row per scan execution
│   ├── AoScanConfig               Per-scope configuration overrides
│   └── AoRemediationEvent         Append-only audit log
├── model/                         REST/DTO objects + enums
├── service/
│   ├── TrufflehogScannerService   Builds/runs the trufflehog subprocess
│   ├── RepositoryPathResolverService   Maps Bitbucket repos to on-disk paths
│   ├── FingerprintService         Stable fingerprint for de-duping
│   ├── FindingService             AO CRUD on findings (upsert, filter, list)
│   ├── RemediationService         Status transitions + audit
│   ├── DashboardService           Aggregations / rollups
│   ├── ScanOrchestrator           Glue: hook/scheduler → scanner → persist
│   ├── TrufflehogConfigurationService   Settings via PluginSettingsFactory
│   └── PluginPermissionService    Centralised access checks
├── condition/                     Web-item visibility conditions
├── hook/PostReceiveScanHook       Async scan on push
├── scheduler/
│   ├── FullScanJobRunner          Daily full scan implementation
│   └── SchedulerBootstrap         Registers + reschedules the cron job
├── servlet/
│   ├── BaseDashboardServlet       Shared HTML shell
│   ├── SystemDashboardServlet
│   ├── ProjectDashboardServlet
│   ├── RepositoryDashboardServlet
│   └── AdminConfigServlet
└── rest/TrufflehogResource        JAX-RS endpoints
```

### Scan lifecycle

1. **Trigger** — `PostReceiveScanHook` (push), `FullScanJobRunner` (cron), or a REST `POST /scan/...` call.
2. **Orchestrator** creates an `AoScanRun` row in `RUNNING` state.
3. **Scanner** invokes `trufflehog git file://<repos>/<repoId> --json [--since-commit ...]` and parses each JSON line.
4. For each raw finding, `FingerprintService` computes a stable hash of `(repoId, detector, file, redacted)`.
5. `FindingService.upsert` either updates the existing row (preserving status / remediation) or inserts a new one.
6. The `AoScanRun` row is finalised with `SUCCESS`/`FAILED`, counts and duration.

### Why a fingerprint instead of commit hash?

The same leaked credential reachable from many commits is logically one secret. The fingerprint deliberately excludes the commit so a re-scan after a rebase or merge does not re-open a finding that the team already triaged.

## Remediation workflow

1. View the finding via the dashboard or its drawer.
2. **Revoke** the credential at the upstream provider.
3. **Rotate** the secret to a new value.
4. **Remove** the secret from source (and, if necessary, rewrite history).
5. Set the finding's status to `FIXED` with a note linking to the rotation ticket. The transition is logged in the audit trail.

`FALSE_POSITIVE` and `ACCEPTED_RISK` exist for triage and policy exceptions respectively. Re-detecting a finding that's marked `FIXED` does **not** auto-reopen it — the fingerprint stays the same; the scanner just bumps `lastSeenAt` and the occurrence count, which is useful signal if a "fixed" secret comes back.

## Notes on security

- Subprocess arguments are passed as a list (no shell). The configured executable and extra args are still rejected if they contain shell metacharacters as defence in depth (`TrufflehogScannerService#safeArg`).
- Findings persist redacted values only, never raw secrets. TruffleHog returns the raw secret in its JSON output but the scanner intentionally discards it before persistence.
- The post-receive hook scans asynchronously so it never blocks a developer's push.
- All REST endpoints check permission via `PluginPermissionService` before reading or modifying.

## Roadmap

- Pre-receive hook to optionally block pushes that introduce verified secrets
- Per-project and per-repository configuration overrides (the `AoScanConfig` entity is already defined)
- Findings export (CSV / JSON / SARIF)
- Slack / webhook notifications on new verified findings
- "Diff scan" mode for pull request decoration
