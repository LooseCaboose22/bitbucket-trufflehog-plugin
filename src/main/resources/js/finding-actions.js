(function (window) {
  'use strict';

  var API = (window.TRUFFLEHOG_CONTEXT || {}).restBase || '/rest/trufflehog/1.0/';

  function html(s) {
    return String(s == null ? '' : s)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  }

  function fetchJson(path, opts) {
    return fetch(API + path, Object.assign({
      headers: { 'Accept': 'application/json' }
    }, opts || {})).then(function (r) {
      if (!r.ok) {
        return r.json().catch(function () { return {}; }).then(function (b) {
          throw new Error(b.error || r.statusText);
        });
      }
      return r.json();
    });
  }

  function fmtTimestamp(ms) {
    if (!ms) return '—';
    return new Date(ms).toLocaleString();
  }

  function statusBadge(status) {
    var s = status || 'OPEN';
    return '<span class="th-status-lozenge th-status-' + html(s) + '">' + html(s.replace(/_/g, ' ')) + '</span>';
  }

  function openDrawer(findingId) {
    var drawer = document.getElementById('th-drawer');
    var title = document.getElementById('th-drawer-title');
    var body = document.getElementById('th-drawer-body');
    drawer.classList.remove('th-drawer--closed');
    title.textContent = 'Finding #' + findingId;
    body.innerHTML = '<p class="th-empty">Loading…</p>';

    fetchJson('findings/' + findingId).then(function (payload) {
      var f = payload.finding;
      var history = payload.history || [];
      title.textContent = (f.detectorName || f.detectorType || 'Secret') + ' in ' + (f.repositorySlug || '?');

      var detail = '<dl class="th-detail-grid">' +
        '<dt>Severity</dt><dd><span class="th-status-lozenge th-severity-' + html(f.severity || 'MEDIUM') + '">' + html(f.severity || 'MEDIUM') + '</span>' +
          (f.verified ? '<span class="th-verified-badge">Verified</span>' : '') + '</dd>' +
        '<dt>Status</dt><dd>' + statusBadge(f.status) + '</dd>' +
        '<dt>Project / Repo</dt><dd>' + html(f.projectKey) + ' / ' + html(f.repositorySlug) + '</dd>' +
        '<dt>File</dt><dd>' + html(f.filePath || '—') + (f.lineNumber ? ':' + f.lineNumber : '') + '</dd>' +
        '<dt>Branch</dt><dd>' + html(f.branch || '—') + '</dd>' +
        '<dt>Commit</dt><dd>' + html(f.commit ? f.commit.substring(0, 12) : '—') + '</dd>' +
        '<dt>Committed by</dt><dd>' + html(f.commitAuthor || '—') + '</dd>' +
        '<dt>Detector</dt><dd>' + html(f.detectorType || '') + ' / ' + html(f.detectorName || '') + '</dd>' +
        '<dt>Redacted</dt><dd><code>' + html(f.redacted || '') + '</code></dd>' +
        '<dt>Occurrences</dt><dd>' + html(f.occurrenceCount || 1) + '</dd>' +
        '<dt>First seen</dt><dd>' + fmtTimestamp(f.discoveredAt) + '</dd>' +
        '<dt>Last seen</dt><dd>' + fmtTimestamp(f.lastSeenAt) + '</dd>' +
      '</dl>';

      var canRemediate = (window.TRUFFLEHOG_CONTEXT || {}).canRemediate;
      var remediate = '';
      if (canRemediate) {
        remediate =
          '<div class="th-drawer-section th-remediate">' +
          '<h3>Remediate</h3>' +
          '<p style="font-size:13px;color:var(--ink-2);margin:0 0 14px">Revoke the credential in your provider, rotate it, and remove it from source history if required. Then update the status below.</p>' +
          '<div class="th-field"><label>New status</label>' +
          '<select id="th-action-status">' +
            ['OPEN', 'FIXED', 'FALSE_POSITIVE', 'ACCEPTED_RISK'].map(function (s) {
              return '<option value="' + s + '"' + (f.status === s ? ' selected' : '') + '>' + s.replace(/_/g, ' ') + '</option>';
            }).join('') +
          '</select></div>' +
          '<div class="th-field"><label>Note</label>' +
          '<textarea id="th-action-note" placeholder="Optional context about this change…">' + html(f.remediationNote || '') + '</textarea></div>' +
          '<div style="display:flex;align-items:center;gap:10px">' +
          '<button class="th-form-btn" type="button" id="th-action-save">Save</button>' +
          '<span class="th-save-status" id="th-action-status-msg"></span>' +
          '</div></div>';
      }

      var historyHtml = '<div class="th-drawer-section">' +
        '<h3>History</h3>' +
        (history.length === 0 ? '<p class="th-empty">No remediation events yet.</p>' :
          history.map(function (h) {
            var auditBits = [];
            if (h.sourceIp)                        auditBits.push('IP ' + html(h.sourceIp));
            if (h.userAgent)                        auditBits.push(html(h.userAgent));
            if (h.projectKey || h.repositorySlug)  auditBits.push(html((h.projectKey || '?') + '/' + (h.repositorySlug || '?')));
            return '<div class="th-history-entry"><div><strong>' + html(h.actor || 'System') + '</strong> &rarr; ' +
                   statusBadge(h.fromStatus) + ' &rarr; ' + statusBadge(h.toStatus) +
                   (h.note ? '<br><span style="font-size:12px;color:var(--ink-2)">' + html(h.note) + '</span>' : '') + '</div>' +
                   (auditBits.length ? '<div class="th-when">' + auditBits.join(' &middot; ') + '</div>' : '') +
                   '<div class="th-when">' + fmtTimestamp(h.occurredAt) + '</div></div>';
          }).join('')) +
        '</div>';

      body.innerHTML = detail + remediate + historyHtml;

      if (canRemediate) {
        document.getElementById('th-action-save').addEventListener('click', function () {
          var newStatus = document.getElementById('th-action-status').value;
          var note = document.getElementById('th-action-note').value;
          var msg = document.getElementById('th-action-status-msg');
          msg.className = 'th-save-status';
          msg.textContent = 'Saving…';
          fetchJson('findings/' + findingId + '/status', {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ status: newStatus, remediationNote: note })
          }).then(function () {
            msg.classList.add('th-save-status--ok');
            msg.textContent = 'Saved';
            if (window.THDashboard && window.THDashboard.refresh) {
              window.THDashboard.refresh();
            }
            setTimeout(function () { openDrawer(findingId); }, 250);
          }).catch(function (err) {
            msg.classList.add('th-save-status--err');
            msg.textContent = 'Failed: ' + err.message;
          });
        });
      }
    }).catch(function (err) {
      body.innerHTML = '<p class="th-save-status--err">Failed to load finding: ' + html(err.message) + '</p>';
    });
  }

  function closeDrawer() {
    document.getElementById('th-drawer').classList.add('th-drawer--closed');
  }

  document.addEventListener('click', function (event) {
    var openBtn = event.target.closest('[data-th-open-finding]');
    if (openBtn) {
      event.preventDefault();
      openDrawer(openBtn.getAttribute('data-th-open-finding'));
      return;
    }
    if (event.target.id === 'th-drawer-close') {
      closeDrawer();
    }
  });

  window.THActions = {
    openDrawer: openDrawer,
    closeDrawer: closeDrawer,
    statusBadge: statusBadge,
    fetchJson: fetchJson,
    fmtTimestamp: fmtTimestamp,
    html: html
  };
})(window);
