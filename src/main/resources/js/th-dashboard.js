(function (window) {
  'use strict';

  var ctx = window.TRUFFLEHOG_CONTEXT || {};
  var cp = ctx.contextPath || '';
  var page = 0;
  var pageSize = 50;
  var lastTotal = 0;
  var sortBy = 'lastSeenAt';
  var sortDirection = 'DESC';

  function api(path, opts) {
    return window.THActions.fetchJson(path, opts);
  }
  function html(s) { return window.THActions.html(s); }
  function statusBadge(s) { return window.THActions.statusBadge(s); }
  function fmt(ms) { return window.THActions.fmtTimestamp(ms); }

  function scopeQuery(extra) {
    var parts = [];
    if (ctx.projectKey)     parts.push('projectKey=' + encodeURIComponent(ctx.projectKey));
    if (ctx.repositorySlug) parts.push('repositorySlug=' + encodeURIComponent(ctx.repositorySlug));
    if (extra) parts.push(extra);
    return parts.length ? '?' + parts.join('&') : '';
  }

  function loadDashboard() {
    var scope = (ctx.scope || 'SYSTEM').toLowerCase();
    return api('dashboard/' + scope + scopeQuery()).then(function (payload) {
      renderSummary(payload.summary || {});
      renderCharts(payload.summary || {});
      renderScans(payload.scanRuns || []);
      if (Array.isArray(payload.projects)) renderProjects(payload.projects);
      if (Array.isArray(payload.topRepositories)) renderTopRepos(payload.topRepositories);
    });
  }

  function loadFindings() {
    var status   = document.getElementById('th-filter-status').value;
    var verified = document.getElementById('th-filter-verified').checked;
    var search   = document.getElementById('th-filter-search').value;
    var qs = scopeQuery(
      'limit=' + pageSize + '&offset=' + (page * pageSize) +
      '&sortBy=' + encodeURIComponent(sortBy) +
      '&sortDirection=' + encodeURIComponent(sortDirection) +
      (status   ? '&status='      + encodeURIComponent(status)  : '') +
      (verified ? '&onlyVerified=true' : '') +
      (search   ? '&detectorType=' + encodeURIComponent(search) : '')
    );
    return api('findings' + qs).then(function (payload) {
      lastTotal = payload.total || 0;
      renderFindings(payload.findings || []);
      updatePager();
    });
  }

  function updateSortHeaders() {
    var ths = document.querySelectorAll('.th-table thead th.th-sortable');
    ths.forEach(function (th) {
      var col = th.getAttribute('data-col');
      var icon = th.querySelector('.th-sort-icon');
      if (col === sortBy) {
        th.classList.add('th-sort-active');
        if (icon) icon.textContent = sortDirection === 'ASC' ? '↑' : '↓';
      } else {
        th.classList.remove('th-sort-active');
        if (icon) icon.textContent = '↕';
      }
    });
  }

  function updatePager() {
    var start = page * pageSize + 1;
    var end   = Math.min((page + 1) * pageSize, lastTotal);
    document.getElementById('th-pageinfo').textContent =
      lastTotal === 0 ? 'No findings' : (start + '–' + end + ' of ' + lastTotal);
    document.getElementById('th-prev').disabled = page === 0;
    document.getElementById('th-next').disabled = end >= lastTotal;
  }

  function renderSummary(s) {
    var card = function (label, value, klass, sub) {
      return '<div class="th-card th-card--' + klass + '">' +
        '<div class="th-card-value">' + (value || 0) + '</div>' +
        '<div class="th-card-label">' + html(label) + '</div>' +
        (sub ? '<div class="th-card-trend">' + html(sub) + '</div>' : '') +
        '</div>';
    };
    var cards = [
      card('Open',           s.open          || 0, 'open',     s.verifiedOpen ? s.verifiedOpen + ' verified' : ''),
      card('Verified open',  s.verifiedOpen  || 0, 'verified', 'Confirmed live credentials'),
      card('Fixed',          s.fixed         || 0, 'fixed'),
      card('False positive', s.falsePositive || 0, 'fp'),
      card('Accepted risk',  s.acceptedRisk  || 0, 'risk')
    ];
    if ((ctx.scope || 'SYSTEM') === 'SYSTEM') {
      cards.push(card('Affected projects',     s.affectedProjects     || 0, 'projects'));
      cards.push(card('Affected repositories', s.affectedRepositories || 0, 'repos'));
    } else if (ctx.scope === 'PROJECT') {
      cards.push(card('Affected repositories', s.affectedRepositories || 0, 'repos'));
    }
    cards.push(card('Last scan', s.lastScanAt ? new Date(s.lastScanAt).toLocaleString() : '—', 'scans'));
    document.getElementById('th-summary').innerHTML = cards.join('');
  }

  function renderCharts(s) {
    var sevColors = { CRITICAL: '#bf2600', HIGH: '#de350b', MEDIUM: '#ff8b00', LOW: '#36b37e', INFO: '#0052cc' };
    var sevOrder  = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFO'];
    var sevSegments = sevOrder
      .filter(function (k) { return (s.bySeverity || {})[k]; })
      .map(function (k)    { return { label: k, value: s.bySeverity[k], color: sevColors[k] }; });
    window.THCharts.donut(document.getElementById('th-chart-severity'), sevSegments);

    var detectorEntries = Object.entries(s.byDetector || {});
    detectorEntries.sort(function (a, b) { return b[1] - a[1]; });
    var detectorBars = detectorEntries.slice(0, 10).map(function (e) {
      return { label: e[0], value: e[1], color: '#0747a6' };
    });
    window.THCharts.bars(document.getElementById('th-chart-detector'), detectorBars);

    var trend = Object.entries(s.openFindingsByDay || {})
      .sort(function (a, b) { return a[0] < b[0] ? -1 : 1; })
      .map(function (e) { return { label: e[0], value: e[1] }; });
    window.THCharts.area(document.getElementById('th-chart-trend'), trend);

    var newSeries = Object.entries(s.newFindingsByDay || {})
      .sort(function (a, b) { return a[0] < b[0] ? -1 : 1; })
      .map(function (e) { return { label: e[0], value: e[1] }; });
    var resolvedSeries = Object.entries(s.resolvedFindingsByDay || {})
      .sort(function (a, b) { return a[0] < b[0] ? -1 : 1; })
      .map(function (e) { return { label: e[0], value: e[1] }; });
    window.THCharts.multiLineArea(document.getElementById('th-chart-activity'), [
      { name: 'New',        color: '#de350b', points: newSeries },
      { name: 'Remediated', color: '#00875a', points: resolvedSeries }
    ]);
  }

  function renderFindings(findings) {
    var body = document.getElementById('th-findings');
    if (!findings.length) {
      body.innerHTML = '<tr><td colspan="9" class="th-empty">No findings match the current filters.</td></tr>';
      return;
    }
    body.innerHTML = findings.map(function (f) {
      var sev = f.severity || 'MEDIUM';
      var repoLink = cp + '/plugins/servlet/trufflehog/dashboard/repository?projectKey=' +
                     encodeURIComponent(f.projectKey || '') + '&repositorySlug=' + encodeURIComponent(f.repositorySlug || '');
      return '<tr data-finding-id="' + f.id + '">' +
        '<td><span class="th-status-lozenge th-severity-' + html(sev) + '">' + html(sev) + '</span>' +
          (f.verified ? '<span class="th-verified-badge">&#10003;</span>' : '') + '</td>' +
        '<td>' + html(f.detectorType || '') + '</td>' +
        '<td class="th-col-dim">' + html(f.projectKey || '') + '</td>' +
        '<td><a class="th-detail-link" href="' + repoLink + '">' + html(f.repositorySlug || '') + '</a></td>' +
        '<td class="th-col-path" title="' + html(f.filePath || '') + '">' + html(f.filePath || '') +
          (f.lineNumber ? '<span class="th-col-dim">:' + f.lineNumber + '</span>' : '') + '</td>' +
        '<td class="th-col-dim">' + html(f.branch || '') + '</td>' +
        '<td class="th-col-dim">' + fmt(f.lastSeenAt) + '</td>' +
        '<td>' + statusBadge(f.status) + '</td>' +
        '<td><a class="th-detail-link" href="#" data-th-open-finding="' + f.id + '">Details</a></td>' +
      '</tr>';
    }).join('');
  }

  function renderScans(scans) {
    var body = document.getElementById('th-scans');
    if (!scans.length) {
      body.innerHTML = '<tr><td colspan="8" class="th-empty">No scans recorded yet.</td></tr>';
      return;
    }
    body.innerHTML = scans.map(function (s) {
      return '<tr>' +
        '<td>' + fmt(s.startedAt) + '</td>' +
        '<td class="th-col-dim">' + html(s.triggerType || '') + '</td>' +
        '<td>' + html(s.projectKey || '') + ' / ' + html(s.repositorySlug || '') + '</td>' +
        '<td>' + statusBadge(s.status) + '</td>' +
        '<td class="td-num">' + (s.findingsTotal    || 0) + '</td>' +
        '<td class="td-num">' + (s.findingsNew      || 0) + '</td>' +
        '<td class="td-num">' + (s.findingsVerified || 0) + '</td>' +
        '<td class="th-col-dim">' + ((s.durationMs || 0) / 1000).toFixed(1) + 's</td>' +
      '</tr>';
    }).join('');
  }

  function renderProjects(rollups) {
    var target = document.getElementById('th-projects');
    if (!target) return;
    if (!rollups.length) {
      target.innerHTML = '<p class="th-empty">No findings have been recorded yet.</p>';
      return;
    }
    target.innerHTML = '<table class="th-ptable">' +
      '<thead><tr>' +
        '<th>Project</th><th class="td-num">Open</th><th class="td-num">Verified</th>' +
        '<th class="td-num">Fixed</th><th class="td-num">False+</th>' +
        '<th class="td-num">Accepted risk</th><th>Last finding</th>' +
      '</tr></thead><tbody>' +
      rollups.map(function (p) {
        return '<tr>' +
          '<td><a href="' + cp + '/plugins/servlet/trufflehog/dashboard/project?projectKey=' +
            encodeURIComponent(p.projectKey) + '">' + html(p.projectKey) + '</a></td>' +
          '<td class="td-num">' + (p.open           || 0) + '</td>' +
          '<td class="td-num">' + (p.verifiedOpen   || 0) + '</td>' +
          '<td class="td-num">' + (p.fixed          || 0) + '</td>' +
          '<td class="td-num">' + (p.falsePositive  || 0) + '</td>' +
          '<td class="td-num">' + (p.acceptedRisk   || 0) + '</td>' +
          '<td class="th-col-dim">' + fmt(p.lastFindingAt) + '</td>' +
        '</tr>';
      }).join('') + '</tbody></table>';
  }

  function renderTopRepos(rows) {
    var target = document.getElementById('th-top-repos');
    if (!target) return;
    if (!rows.length) {
      target.innerHTML = '<p class="th-empty">No findings yet.</p>';
      return;
    }
    target.innerHTML = '<table class="th-ptable">' +
      '<thead><tr>' +
        '<th>Repository</th><th class="td-num">Open</th>' +
        '<th class="td-num">Verified</th><th>Last finding</th>' +
      '</tr></thead><tbody>' +
      rows.map(function (r) {
        var link = cp + '/plugins/servlet/trufflehog/dashboard/repository?projectKey=' +
                   encodeURIComponent(r.projectKey) + '&repositorySlug=' + encodeURIComponent(r.repositorySlug);
        return '<tr>' +
          '<td><a href="' + link + '">' + html(r.projectKey) + ' / ' + html(r.repositorySlug) + '</a></td>' +
          '<td class="td-num">' + (r.openCount   || 0) + '</td>' +
          '<td class="td-num">' + (r.verifiedOpen || 0) + '</td>' +
          '<td class="th-col-dim">' + fmt(r.lastFindingAt) + '</td>' +
        '</tr>';
      }).join('') + '</tbody></table>';
  }

  function refresh() {
    return Promise.all([loadDashboard(), loadFindings()]);
  }

  function showFlag(title, message, type) {
    if (window.AJS && window.AJS.flag) {
      window.AJS.flag({ type: type || 'info', title: title, body: message, close: 'auto' });
    }
  }

  function debounce(fn, ms) {
    var t;
    return function () {
      var args = arguments, self = this;
      clearTimeout(t);
      t = setTimeout(function () { fn.apply(self, args); }, ms);
    };
  }

  function bind() {
    // Filter controls
    document.getElementById('th-filter-status').addEventListener('change', function () { page = 0; loadFindings(); });
    document.getElementById('th-filter-verified').addEventListener('change', function () { page = 0; loadFindings(); });
    document.getElementById('th-filter-search').addEventListener('input', debounce(function () { page = 0; loadFindings(); }, 280));

    // Pager
    document.getElementById('th-prev').addEventListener('click', function () { if (page > 0) { page--; loadFindings(); } });
    document.getElementById('th-next').addEventListener('click', function () {
      if ((page + 1) * pageSize < lastTotal) { page++; loadFindings(); }
    });

    // Refresh
    var refreshBtn = document.getElementById('th-refresh');
    if (refreshBtn) refreshBtn.addEventListener('click', function () { refresh(); });

    // Sortable column headers
    document.querySelectorAll('.th-table thead th.th-sortable').forEach(function (th) {
      th.addEventListener('click', function () {
        var col = this.getAttribute('data-col');
        if (col === sortBy) {
          sortDirection = sortDirection === 'ASC' ? 'DESC' : 'ASC';
        } else {
          sortBy = col;
          sortDirection = 'DESC';
        }
        page = 0;
        updateSortHeaders();
        loadFindings();
      });
    });

    // Scan buttons
    var scanAll = document.getElementById('th-scan-all');
    if (scanAll) {
      scanAll.addEventListener('click', function () {
        scanAll.disabled = true;
        api('scan/all', { method: 'POST' })
          .then(function () { showFlag('Scan queued', 'Scanning all repositories.', 'success'); })
          .catch(function (e) { showFlag('Scan failed', e.message, 'error'); })
          .finally(function () { setTimeout(function () { scanAll.disabled = false; refresh(); }, 1200); });
      });
    }
    var scanProj = document.getElementById('th-scan-project');
    if (scanProj) {
      scanProj.addEventListener('click', function () {
        scanProj.disabled = true;
        api('scan/project/' + encodeURIComponent(ctx.projectKey), { method: 'POST' })
          .then(function () { showFlag('Scan queued', 'Scanning project ' + ctx.projectKey, 'success'); })
          .catch(function (e) { showFlag('Scan failed', e.message, 'error'); })
          .finally(function () { setTimeout(function () { scanProj.disabled = false; refresh(); }, 1200); });
      });
    }
    var scanRepo = document.getElementById('th-scan-repo');
    if (scanRepo) {
      scanRepo.addEventListener('click', function () {
        scanRepo.disabled = true;
        api('scan/repository/' + encodeURIComponent(ctx.projectKey) + '/' + encodeURIComponent(ctx.repositorySlug),
            { method: 'POST' })
          .then(function () { showFlag('Scan queued', 'Scanning ' + ctx.repositorySlug, 'success'); })
          .catch(function (e) { showFlag('Scan failed', e.message, 'error'); })
          .finally(function () { setTimeout(function () { scanRepo.disabled = false; refresh(); }, 1200); });
      });
    }
  }

  document.addEventListener('DOMContentLoaded', function () {
    updateSortHeaders();
    bind();
    refresh();
  });

  window.THDashboard = { refresh: refresh, loadFindings: loadFindings };
})(window);
