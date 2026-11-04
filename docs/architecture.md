# Architecture Guide — TruffleHog Secret Scanner

This guide covers the internal design of the plugin for developers who need to maintain, extend, or understand it.

---

## Table of contents

1. [High-level overview](#high-level-overview)
2. [Module map](#module-map)
3. [Package structure](#package-structure)
4. [Data model](#data-model)
5. [REST API reference](#rest-api-reference)
6. [Scan lifecycle](#scan-lifecycle)
7. [Fingerprinting algorithm](#fingerprinting-algorithm)
8. [Finding deduplication and upsert](#finding-deduplication-and-upsert)
9. [Permission model](#permission-model)
10. [JavaScript UI architecture](#javascript-ui-architecture)
11. [Plugin descriptor](#plugin-descriptor)
12. [Build system](#build-system)
13. [Test architecture](#test-architecture)
14. [Key design decisions](#key-design-decisions)

---

## High-level overview

```
                          ┌─────────────────────────────────────────────┐
                          │              Bitbucket Data Center           │
                          │                                              │
  git push ──────────────►│  PostReceiveScanHook                        │
                          │          │                                   │
  Quartz cron ───────────►│  FullScanJobRunner                          │
                          │          │                                   │
  REST POST /scan/* ──────►│  TrufflehogResource                        │
                          │          │                                   │
                          │          ▼                                   │
                          │  ScanOrchestrator                           │
                          │     ├─ RepositoryPathResolverService        │
                          │     ├─ TrufflehogConfigurationService       │
                          │     └─ TrufflehogScannerService             │
                          │              │  subprocess                   │
                          │              ▼                               │
                          │         trufflehog binary  ◄── bare repo    │
                          │              │  JSONL stdout                 │
                          │              ▼                               │
                          │  FingerprintService                         │
                          │  FindingService (Active Objects upsert)     │
                          │              │                               │
                          │              ▼                               │
                          │  ┌─────────────────────┐                   │
                          │  │  Bitbucket Database  │                   │
                          │  │  Finding / ScanRun / │                   │
                          │  │  Remediation /       │                   │
                          │  │  ScanConfig          │                   │
                          │  └─────────────────────┘                   │
                          │              ▲                               │
                          │  REST API  ──┘                              │
                          │  Servlets ──► HTML + TRUFFLEHOG_CONTEXT     │
                          │              ▲                               │
  Browser ───────────────►│  dashboard.js + charts.js + admin.js       │
                          └─────────────────────────────────────────────┘
```

---

## Module map

| Layer | Responsibility |
|-------|----------------|
| **`hook/`** | Intercepts `git push` events, fires async scans |
| **`scheduler/`** | Registers and manages the Quartz cron job |
| **`service/ScanOrchestrator`** | Coordinates the end-to-end scan pipeline |
| **`service/TrufflehogScannerService`** | Spawns TruffleHog subprocess, parses JSONL output |
| **`service/FingerprintService`** | Produces deterministic SHA-256 deduplication keys |
| **`service/FindingService`** | Active Objects CRUD for findings |
| **`service/RemediationService`** | Status transitions + audit trail |
| **`service/DashboardService`** | Aggregations for dashboard KPIs and charts |
| **`service/PluginPermissionService`** | Centralised authorisation checks |
| **`service/TrufflehogConfigurationService`** | Reads/writes plugin settings via `PluginSettingsFactory` |
| **`service/RepositoryPathResolverService`** | Maps repository ID to on-disk bare repo path |
| **`rest/TrufflehogResource`** | JAX-RS REST endpoints |
| **`servlet/`** | Server-side HTML rendering for dashboards and admin page |
| **`ao/`** | Active Objects entity interfaces |
| **`model/`** | DTOs, enums, value objects |
| **`js/`** | Client-side dashboard logic, charts, drawer, admin form |
| **`css/`** | Design system and component styles |

---

## Package structure

```
src/main/java/com/acme/bitbucket/trufflehog/
├── ao/
│   ├── AoSecretFinding.java        Persisted finding (one per fingerprint+repo)
│   ├── AoScanRun.java              Scan execution record
│   ├── AoScanConfig.java           Per-scope config overrides (not yet UI-exposed)
│   └── AoRemediationEvent.java     Immutable audit event
├── model/
│   ├── SecretFinding.java          REST DTO
│   ├── TrufflehogSettings.java     Global settings POJO
│   ├── TruffleHogRawFinding.java   TruffleHog JSONL output POJO
│   ├── ScanRunDto.java             Dashboard scan run DTO
│   ├── ScanSummary.java            Dashboard summary counts DTO
│   ├── RemediationEventDto.java    Audit event DTO
│   ├── RemediationContext.java     Request metadata (IP, user agent)
│   ├── FindingFilter.java          Query parameters for finding list
│   ├── FindingStatus.java          Enum: OPEN / FIXED / FALSE_POSITIVE / ACCEPTED_RISK
│   ├── Severity.java               Enum: CRITICAL / HIGH / MEDIUM / LOW / INFO
│   ├── ScanScope.java              Enum: SYSTEM / PROJECT / REPOSITORY
│   ├── ScanTrigger.java            Enum: SCHEDULED / MANUAL / POST_RECEIVE / BACKFILL
│   └── ScanStatus.java             Enum: QUEUED / RUNNING / SUCCESS / FAILED / CANCELLED
├── service/
│   ├── TrufflehogScannerService.java
│   ├── FingerprintService.java
│   ├── FindingService.java
│   ├── RemediationService.java
│   ├── DashboardService.java
│   ├── ScanOrchestrator.java
│   ├── RepositoryPathResolverService.java
│   ├── TrufflehogConfigurationService.java
│   └── PluginPermissionService.java
├── servlet/
│   ├── BaseDashboardServlet.java
│   ├── SystemDashboardServlet.java
│   ├── ProjectDashboardServlet.java
│   ├── RepositoryDashboardServlet.java
│   └── AdminConfigServlet.java
├── rest/
│   └── TrufflehogResource.java
├── hook/
│   └── PostReceiveScanHook.java
└── scheduler/
    ├── SchedulerBootstrap.java
    └── FullScanJobRunner.java

src/main/resources/
├── atlassian-plugin.xml
├── META-INF/spring/plugin-context.xml
├── js/
│   ├── dashboard.js
│   ├── finding-actions.js
│   ├── charts.js
│   └── admin.js
└── css/
    └── dashboard.css
```

---

## Data model

### AoSecretFinding (`Finding` table)

The central entity. One row per unique secret per repository (deduplicated by fingerprint).

```
Column              Type        Notes
─────────────────   ─────────   ──────────────────────────────────────────────
ID                  int PK
FINGERPRINT         varchar     SHA-256 hex; unique per repository
REPOSITORY_ID       int         Bitbucket repository numeric ID (indexed)
PROJECT_KEY         varchar     Bitbucket project key (indexed)
REPOSITORY_SLUG     varchar     Bitbucket repository slug
DETECTOR_TYPE       varchar     e.g. "AWS", "GithubOauth"
DETECTOR_NAME       varchar     More specific sub-type
VERIFIED            boolean     True if TruffleHog verified the credential
REDACTED            text        Masked value, e.g. "AKIA****EXAMPLE"
FILE_PATH           text        Relative path inside the repository
LINE_NUMBER         int
BRANCH              varchar     Branch of first detection
COMMIT              varchar(80) Abbreviated commit SHA
COMMIT_AUTHOR       varchar
COMMIT_TIMESTAMP    bigint      Unix epoch ms
SEVERITY            varchar(40) FindingStatus string value
STATUS              varchar(40) FindingStatus string value (indexed)
REMEDIATION_NOTE    text
RESOLVED_BY         varchar
RESOLVED_AT         bigint      Unix epoch ms; 0 if not resolved
DISCOVERED_AT       bigint      Unix epoch ms (indexed via LAST_SEEN_AT)
LAST_SEEN_AT        bigint      Unix epoch ms (indexed)
OCCURRENCE_COUNT    int         Incremented each time the fingerprint is re-seen
```

**Indexes:** `FINGERPRINT`, `REPOSITORY_ID`, `PROJECT_KEY`, `STATUS`, `LAST_SEEN_AT`

### AoScanRun (`ScanRun` table)

One row per scan execution.

```
Column              Type        Notes
─────────────────   ─────────   ──────────────────────────────────────────────
ID                  int PK
REPOSITORY_ID       int         (indexed)
PROJECT_KEY         varchar     (indexed)
REPOSITORY_SLUG     varchar
TRIGGER_TYPE        varchar(40) ScanTrigger string value
TRIGGERED_BY        varchar     Username or "system"
STARTED_AT          bigint      Unix epoch ms (indexed)
FINISHED_AT         bigint      Unix epoch ms
DURATION_MS         bigint
STATUS              varchar(40) ScanStatus string value
FINDINGS_TOTAL      int         Total findings seen in this scan
FINDINGS_VERIFIED   int
FINDINGS_NEW        int         Newly inserted (not upserted) findings
ERROR_MESSAGE       text        Set on FAILED status
REF_NAME            varchar(80) Set for push-triggered scans
FROM_COMMIT         varchar(80) Set for push-triggered scans
TO_COMMIT           varchar(80) Set for push-triggered scans
```

### AoScanConfig (`ScanConfig` table)

Per-scope configuration overrides. Not yet surfaced in the UI but the schema is in place for future per-project / per-repository settings.

```
Column                   Type    Notes
──────────────────────   ──────  ─────────────────────────────
ID                       int PK
SCOPE                    varchar SYSTEM / PROJECT / REPOSITORY (indexed)
SCOPE_KEY                varchar Empty for SYSTEM; projectKey or projectKey/slug (indexed)
ENABLED                  boolean
ONLY_VERIFIED            boolean
BLOCK_PUSH_ON_FINDING    boolean
INCLUDE_PATTERNS         text    JSON array of glob patterns
EXCLUDE_PATTERNS         text    JSON array of glob patterns
ALLOWLIST_FINGERPRINTS   text    JSON array of fingerprint hex strings
```

### AoRemediationEvent (`Remediation` table)

Immutable audit log — rows are never updated or deleted.

```
Column           Type        Notes
──────────────   ─────────   ──────────────────────────────────
ID               int PK
FINDING_ID       int         FK to AoSecretFinding (indexed)
FROM_STATUS      varchar(40)
TO_STATUS        varchar(40)
ACTOR            varchar     Username or "system"
NOTE             text
PROJECT_KEY      varchar(80) Denormalized for fast project-level queries (indexed)
REPOSITORY_SLUG  varchar     (indexed)
SOURCE_IP        varchar(64)
USER_AGENT       varchar(450)
OCCURRED_AT      bigint      Unix epoch ms (indexed)
```

---

## REST API reference

Base path: `/rest/trufflehog/1.0`

All endpoints accept and return `application/json`. Authentication uses Bitbucket's standard session/basic-auth. CSRF protection: `X-Atlassian-Token: no-check` header required on mutating requests.

### Dashboard

#### `GET /dashboard/{scope}`

Returns aggregated dashboard data for a given scope.

**Path params:** `scope` = `system` | `project` | `repository`

**Query params:**
| Param | Required for scope | Description |
|-------|--------------------|-------------|
| `projectKey` | project, repository | Bitbucket project key |
| `repositorySlug` | repository | Repository slug |

**Response:**
```json
{
  "summary": {
    "open": 42,
    "fixed": 18,
    "falsePositive": 3,
    "acceptedRisk": 1,
    "verifiedOpen": 7
  },
  "scanRuns": [ { ...ScanRunDto } ],
  "projects": [ { "projectKey": "...", "openCount": 5 } ],
  "topRepositories": [ { "repositorySlug": "...", "openCount": 3 } ]
}
```

`projects` and `topRepositories` are only populated for the `system` scope. `projects` is only in the `system` response.

---

### Findings

#### `GET /findings`

Paginated filtered list of findings.

**Query params:**
| Param | Type | Default | Description |
|-------|------|---------|-------------|
| `projectKey` | string | — | Filter to project |
| `repositorySlug` | string | — | Filter to repository (requires `projectKey`) |
| `status` | string | — | Filter: `OPEN` / `FIXED` / `FALSE_POSITIVE` / `ACCEPTED_RISK` |
| `detectorType` | string | — | Filter by detector type (exact match) |
| `onlyVerified` | boolean | false | If true, return only verified findings |
| `sortBy` | string | `lastSeenAt` | Column: `lastSeenAt`, `discoveredAt`, `severity`, `status`, `detectorType`, `projectKey`, `repositorySlug` |
| `sortDirection` | string | `DESC` | `ASC` or `DESC` |
| `limit` | int | 25 | Page size (max 100) |
| `offset` | int | 0 | Zero-based offset |

**Response:**
```json
{
  "findings": [ { ...SecretFinding } ],
  "total": 142
}
```

#### `GET /findings/{id}`

Returns a single finding with its full remediation history.

**Response:**
```json
{
  "finding": { ...SecretFinding },
  "history": [ { ...RemediationEventDto } ]
}
```

#### `PUT /findings/{id}/status`

Updates the finding's status and creates an audit event.

**Body:**
```json
{
  "status": "FIXED",
  "remediationNote": "Credential revoked in AWS IAM on 2026-06-18"
}
```

**Validation:** `remediationNote` must be ≥ 8 characters for any status other than `OPEN`.

**Response:** `200 OK` with the updated `SecretFinding`.

#### `GET /findings/{id}/history`

Returns only the remediation event history for a finding (no finding object).

**Response:** Array of `RemediationEventDto`.

---

### Scan triggers

All three endpoints return `202 Accepted` immediately. Scans run asynchronously.

#### `POST /scan/all`
Triggers a full-cluster scan. Requires global `SYS_ADMIN`.

#### `POST /scan/project/{projectKey}`
Triggers a scan of all repositories in the project. Requires project `ADMIN`.

#### `POST /scan/repository/{projectKey}/{repositorySlug}`
Triggers a scan of a single repository. Requires repository `ADMIN`.

---

### Settings

#### `GET /settings`

Returns the current plugin configuration. Requires global `SYS_ADMIN`.

**Response:** `TrufflehogSettings` JSON object.

#### `PUT /settings`

Saves plugin configuration and reschedules the cron job. Requires global `SYS_ADMIN`.

**Body:** `TrufflehogSettings` JSON object (all fields).

**Response:** `200 OK` with the saved settings.

---

## Scan lifecycle

```
Trigger (hook / scheduler / REST)
         │
         ▼
ScanOrchestrator.scanRepository(repo, trigger, [refName, fromCommit, toCommit])
         │
         ├─► Create AoScanRun (status=RUNNING, startedAt=now)
         │
         ├─► RepositoryPathResolverService.resolve(repo.getId())
         │       → {sharedHome}/data/repositories/{id}   (or repositoriesRoot override)
         │
         ├─► TrufflehogScannerService.scan(path, settings, [fromCommit, branch])
         │       │
         │       ├─► git clone --local {barePath} {tempDir}   (TruffleHog needs non-bare)
         │       │
         │       ├─► Build args:
         │       │     trufflehog git --json --no-update
         │       │       [--since-commit <fromCommit>]   ← incremental only
         │       │       [--branch <refName>]            ← incremental only
         │       │       [--only-verified]               ← from extraArgs
         │       │       file://{tempDir}
         │       │
         │       ├─► Spawn Process, set timeout watchdog
         │       │
         │       ├─► Read stdout line-by-line (JSONL)
         │       │       Each line: parse JSON → TruffleHogRawFinding
         │       │       Skip non-JSON lines (TruffleHog progress messages)
         │       │       Skip malformed JSON (log WARN)
         │       │
         │       └─► Return List<TruffleHogRawFinding>
         │
         ├─► For each raw finding:
         │       │
         │       ├─► FingerprintService.fingerprintFor(repoId, finding)
         │       │       SHA-256( repoId | detectorType | detectorName | filePath | redacted )
         │       │
         │       ├─► FindingService.upsert(fingerprint, finding)
         │       │       IF fingerprint+repoId exists:
         │       │           UPDATE lastSeenAt, occurrenceCount++    ← preserve status
         │       │       ELSE:
         │       │           INSERT new row, status=OPEN, discoveredAt=lastSeenAt=now
         │       │           findingsNew++
         │       │
         │       └─► findingsTotal++
         │
         ├─► Delete tempDir clone
         │
         └─► Finalize AoScanRun:
               status = SUCCESS (or FAILED if exception)
               finishedAt = now
               durationMs = finishedAt - startedAt
               findingsTotal, findingsVerified, findingsNew
               errorMessage (if FAILED)
```

---

## Fingerprinting algorithm

The fingerprint is a 64-character lowercase hex SHA-256 digest computed from a pipe-delimited string:

```
{repositoryId}|{detectorType}|{detectorName}|{filePath}|{redacted}
```

**Intentionally excluded from the fingerprint:**
- Commit SHA — the same secret committed multiple times is one finding
- Branch — the same secret on multiple branches is one finding
- Line number — secrets don't move between lines when files are reformatted

**Null handling:** null `detectorType` and empty string `detectorType` produce the same fingerprint (normalised to empty string before hashing).

The fingerprint is unique **per repository** — the same secret in two different repositories produces two distinct findings.

```java
// FingerprintService core logic
String raw = repositoryId + "|"
    + nvl(detectorType) + "|"
    + nvl(detectorName) + "|"
    + nvl(filePath) + "|"
    + nvl(redacted);
byte[] hash = MessageDigest.getInstance("SHA-256").digest(
    raw.getBytes(StandardCharsets.UTF_8));
return Hex.encodeHexString(hash);  // 64 lowercase hex chars
```

---

## Finding deduplication and upsert

`FindingService.upsert()` enforces that the database holds exactly one row per (fingerprint, repositoryId) pair:

```
upsert(fingerprint, repoId, rawFinding):
  existing = ao.find(AoSecretFinding.class,
      Query.select().where("FINGERPRINT = ? AND REPOSITORY_ID = ?",
          fingerprint, repoId))

  if existing != null:
      existing.setLastSeenAt(now)
      existing.setOccurrenceCount(existing.getOccurrenceCount() + 1)
      existing.save()
      return false  // not new

  else:
      ao.create(AoSecretFinding.class, ...)
      return true   // new finding
```

**Status is never touched on re-upsert.** If a user marks a finding FIXED and it is rediscovered on the next scan, the status remains FIXED. This is deliberate: the finding reappearing is a signal for the user, not an automatic state machine transition. The `lastSeenAt` timestamp and `occurrenceCount` serve as the signal.

---

## Permission model

`PluginPermissionService` wraps Bitbucket's `SecurityService`, `ProjectService`, and `RepositoryService` to provide a single authorisation surface.

```java
// Example — system dashboard access
canViewSystemDashboard():
    return securityService.hasGlobalPermission(Permission.ADMIN)

// Example — project remediation
canRemediateProject(projectKey):
    return securityService.hasProjectPermission(Permission.PROJECT_ADMIN, projectKey)
        || securityService.hasGlobalPermission(Permission.SYS_ADMIN)
```

**Inheritance:** higher permissions always imply lower ones:
- `SYS_ADMIN` implies `ADMIN`, implies all project/repo permissions
- `PROJECT_ADMIN` implies `PROJECT_WRITE`, `PROJECT_READ` for that project
- `REPO_ADMIN` implies `REPO_WRITE`, `REPO_READ` for that repository

Every servlet checks permission in `service()` before touching the `PrintWriter`. On denial it calls `response.sendError(403)` without writing any response body.

Every REST endpoint checks permission via `PluginPermissionService` before executing. Unauthorised requests return `403 Forbidden`.

---

## JavaScript UI architecture

The front-end is deliberately framework-free — plain ES5-compatible JavaScript with no bundler. This avoids dependency on Node.js tooling during the plugin build and keeps the resource download small.

### Initialisation

The servlet injects a `<script>` block into the page HTML:

```javascript
window.TRUFFLEHOG_CONTEXT = {
  scope: 'SYSTEM',            // or PROJECT / REPOSITORY
  projectKey: '',
  repositorySlug: '',
  canRemediate: true,
  contextPath: '/bitbucket',
  restBase: '/bitbucket/rest/trufflehog/1.0/'
};
```

All JavaScript files read from `TRUFFLEHOG_CONTEXT` rather than hard-coding paths.

### Module responsibilities

**`dashboard.js`**
- Entry point: calls `loadDashboard()` on DOM ready.
- `loadDashboard()` → `api('dashboard/' + scope)` → populates KPI cards, calls chart renderers, loads projects/repos panels, then calls `loadFindings()`.
- `loadFindings()` builds query string from current filter/sort/page state, fetches `/findings`, calls `renderFindings()`.
- Column sort: module-level `sortBy` and `sortDirection` variables; `.th-sortable` click handler updates state, calls `updateSortHeaders()` then `loadFindings()`.
- Filter inputs debounced 400 ms; checkbox immediate.
- Exports `window.THActions` for cross-file use: `fetchJson`, `html` (safe HTML builder), `statusBadge`, `fmtTimestamp`.

**`finding-actions.js`**
- Listens for `[data-th-open-finding]` clicks → calls `openDrawer(id)`.
- `openDrawer()` → `GET /findings/{id}` → populates `#th-drawer` with detail grid + remediation form + history.
- Save button → `PUT /findings/{id}/status` → refreshes drawer content + refreshes findings table.
- Close button and outside-click → adds `th-drawer--closed` class.

**`charts.js`**
- Pure SVG rendering, no external chart library.
- `donut(data, containerId)` — severity breakdown with percentage legend.
- `bars(data, containerId)` — horizontal bar chart for top detectors.
- `area(data, containerId)` — area chart for open findings trend.
- `multiLineArea(data, containerId)` — two-line area chart for new vs remediated.
- All charts are responsive via SVG `viewBox` and CSS `width: 100%`.

**`admin.js`**
- Reads all form inputs on save button click.
- Serialises booleans (`checked`) and integers (`parseInt`) correctly.
- `PUT /rest/trufflehog/1.0/settings` with `Content-Type: application/json`.
- Shows `#th-save-status`: "Saving…" → "Saved" / "Failed: {message}".

### HTML injection safety

All user-controlled data rendered server-side passes through escape helpers in `BaseDashboardServlet`:

```java
private String escape(String s)     // HTML entity encoding
private String escapeAttr(String s) // HTML attribute encoding
private String escapeJs(String s)   // JavaScript string encoding
```

Client-side rendering uses a `html()` helper that builds DOM elements via `document.createElement` + `textContent` — never `innerHTML` with unescaped data.

---

## Plugin descriptor

`atlassian-plugin.xml` declares all Bitbucket extension points:

| Element | Key | Purpose |
|---------|-----|---------|
| `<ao>` | — | Registers 4 Active Objects entity interfaces |
| `<rest>` | `trufflehog-rest` | Mounts `TrufflehogResource` at `/rest/trufflehog/1.0/` |
| `<servlet>` | `system-dashboard-servlet` | `/plugins/servlet/trufflehog/dashboard/system` |
| `<servlet>` | `project-dashboard-servlet` | `/plugins/servlet/trufflehog/dashboard/project` |
| `<servlet>` | `repo-dashboard-servlet` | `/plugins/servlet/trufflehog/dashboard/repo` |
| `<servlet>` | `admin-config-servlet` | `/plugins/servlet/trufflehog/admin` |
| `<repository-hook>` | `trufflehog-post-receive-hook` | Async post-receive scanner |
| `<web-item>` | admin nav links | Adds links to Bitbucket Admin menu |
| `<web-item>` | project/repo tab | Adds "Secret Scanner" tab to project/repo sidebars |
| `<web-resource>` | `dashboard-resources` | Bundles JS and CSS served by Bitbucket's resource servlet |

Spring component scanning is configured in `META-INF/spring/plugin-context.xml` using `<context:component-scan>` over the `com.acme.bitbucket.trufflehog` base package.

---

## Build system

The project uses the **Atlassian Plugin SDK** (Maven wrapper):

```bash
# Full build with tests
mvn clean package

# Skip tests (faster)
mvn -B clean package -DskipTests

# Run local Bitbucket instance (atlas-run)
mvn bitbucket:run

# Hot-deploy to running instance
mvn bitbucket:install
```

**Key POM configuration:**

| Property | Value |
|----------|-------|
| Java target/source | 11 |
| Bitbucket version | 8.19.9 |
| Jackson | 2.15.4 (bundled — overrides Bitbucket's Jackson 1.x) |
| Active Objects | 3.2.10 |
| Atlassian Scheduler | 1.6.0 |
| JUnit | 4.13.2 |
| Mockito | 4.11.0 |

Jackson 2.x is declared as a `compile`-scope dependency (not `provided`) because Bitbucket ships Jackson 1.x, which lacks the annotation support required for the plugin's DTOs. The plugin bundles its own Jackson 2.x JAR.

---

## Test architecture

### Unit tests (JUnit 4 + Mockito)

Located in `src/test/java/`, mirroring the main package structure:

| Test class | What it covers |
|------------|----------------|
| `SeverityTest` | `fromVerified()`, `fromString()`, null/unknown handling |
| `FingerprintServiceTest` | Determinism, hex format, commit/branch exclusion, null fields |
| `TrufflehogScannerServiceTest` | JSONL parsing via mocked `Process`, arg injection prevention |
| `FindingServiceTest` | DTO mapping, `resolveOrderBy()` normalization, `FindingFilter` defaults |
| `RemediationServiceTest` | Status transitions, audit event creation, `trimTo()` |
| `BaseDashboardServletTest` | HTML structure, context injection, permission denial, escape helpers |

**Mocking approach:**
- `AoSecretFinding`, `AoRemediationEvent`, and all Bitbucket service interfaces are mocked with Mockito.
- `TrufflehogScannerService` tests use `ByteArrayInputStream` to feed controlled JSONL through a mocked `Process`.
- Private methods (`parseOutput`, `safeArg`, `resolveOrderBy`, `trimTo`) are tested via reflection.

**Run:**
```bash
mvn test
# or specific class:
mvn test -Dtest=FingerprintServiceTest
```

### E2E tests (Playwright)

Located in `e2e/`, targeting a running Bitbucket instance at `http://localhost:7990/bitbucket`.

| Spec | Coverage |
|------|----------|
| `api.spec.ts` | REST API contract: shape, filters, sort, pagination, 404, auth guard |
| `dashboard.spec.ts` | System dashboard: cards, charts, table, filter bar, sort, pager, CSS |
| `findings.spec.ts` | Filter, sort, drawer open/close, detail fields, history, pagination, badges |
| `admin.spec.ts` | Form cards, field types, save, round-trip, CSS, JS errors |

**Authentication:** `global-setup.ts` navigates to `/login`, fills credentials, and saves the session to `tests/helpers/auth.json` via `storageState`. All browser tests load this state.

**API tests** use a separate `apiContext()` with `Authorization: Basic base64(admin:admin)` and `X-Atlassian-Token: no-check` — no browser state needed.

**Run:**
```bash
cd e2e
# Install dependencies (Linux / WSL — requires native Node.js, not Windows interop)
/usr/bin/npm install
/usr/bin/npx playwright install chromium

# Run setup (creates auth.json)
PLAYWRIGHT_BROWSERS_PATH=/home/y7umyp/.cache/ms-playwright \
BITBUCKET_URL=http://localhost:7990/bitbucket \
/usr/bin/npx playwright test --project=setup

# Run full suite
PLAYWRIGHT_BROWSERS_PATH=/home/y7umyp/.cache/ms-playwright \
BITBUCKET_URL=http://localhost:7990/bitbucket \
/usr/bin/npx playwright test --reporter=list
```

**Note:** On Windows WSL2, always use the native Linux Node.js binary (`/usr/bin/node`). The Windows Node interop path causes CMD.EXE to launch, which tries to write test results to `C:\Windows\` and fails with EPERM.

---

## Key design decisions

### Why Active Objects instead of a direct JDBC DAO?

Active Objects is Bitbucket's recommended persistence layer for plugins. It:
- Works with all Bitbucket-supported databases (PostgreSQL, Oracle, MSSQL, MySQL, H2) without writing dialect-specific SQL
- Handles schema creation and incremental migration automatically
- Is cluster-safe — no manual connection pooling or transaction management
- Integrates with Atlassian's upgrade framework for AO schema versioning

The tradeoff is that complex aggregation queries (e.g. daily trend counts) are less efficient than native SQL. For this plugin the data volumes are small enough that this is acceptable.

### Why fingerprint by detector+file+redacted rather than by commit+line?

The goal is stable finding identity across re-scans. A secret that:
- Was committed 6 months ago and still exists should show as one finding with `occurrenceCount` growing
- Was refactored from `src/main/Config.java:42` to `src/main/Config.java:50` should still be the same finding
- Appears on three branches should be one finding per repository, not three

Excluding commit SHA and line number achieves this. Excluding branch achieves cross-branch deduplication. The downside is that two secrets of the same type with the same redacted value in the same file (very unlikely) would collide — acceptable for the use case.

### Why not store the raw secret?

Storing live credentials in a database creates an extremely sensitive target. Bitbucket databases are typically backed up and replicated. TruffleHog's redacted value (e.g. `AKIA****EXAMPLE`) provides enough context to identify and rotate the credential without the risk of the database itself becoming a credential store.

### Why inline SVG charts instead of a chart library?

The plugin runs in Bitbucket's OSGi classloader environment. Loading a chart library (Chart.js, D3.js) via the Atlassian web resource system requires either bundling it (adding ~200 KB to every dashboard page load) or declaring it as a Bitbucket context dependency (fragile — depends on Bitbucket's internal resource versions). The inline SVG approach adds zero bytes to the network transfer for external libraries, has no version coupling, and the four chart types needed are simple enough to implement in ~200 lines.

### Why server-rendered HTML for dashboards instead of a SPA?

Bitbucket Data Center's web item system expects servlets at known URL paths. The plugin registers servlets to satisfy this contract, get breadcrumb injection, and participate in Bitbucket's session/permission model without implementing a separate auth layer. The data-heavy portions (findings list, charts) are loaded dynamically via REST calls from the browser.

### Why `RUN_ONCE_PER_CLUSTER` for the scheduler?

Full-cluster scans are CPU and I/O intensive. Running the scan on all nodes simultaneously would multiply the load without producing different results (Active Objects `upsert` is idempotent but concurrent upserts would produce unnecessary lock contention). The Atlassian Scheduler's `RUN_ONCE_PER_CLUSTER` policy ensures exactly one node runs the job, regardless of cluster size.
