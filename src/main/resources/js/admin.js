(function () {
  'use strict';

  var BOOL_FIELDS = [
    'scanOnPush', 'blockPushOnVerifiedFinding', 'autoFingerprintRotation',
    'notifyCommitter', 'notifyAdmins', 'notifyInfosec',
    'enabledGlobally', 'enableGlobAllowlist',
    'smtpTls'
  ];
  var INT_FIELDS = [
    'maxConcurrentScans', 'scanTimeoutMinutes',
    'smtpPort', 'digestHour', 'maxFileSizeKb', 'maxTotalSizeMb'
  ];

  function restBase() {
    return (window.TRUFFLEHOG_CONTEXT || {}).restBase || '/rest/th-scanner/1.0/';
  }

  function payload() {
    var form = document.getElementById('th-settings-form');
    var data = {};

    Array.from(form.querySelectorAll('input')).forEach(function (el) {
      if (!el.name) return;
      if (el.type === 'checkbox') {
        data[el.name] = !!el.checked;
      } else if (el.type === 'password' && !el.value) {
        // omit blank passwords — server preserves existing value
      } else if (INT_FIELDS.indexOf(el.name) >= 0) {
        data[el.name] = parseInt(el.value, 10) || 0;
      } else {
        data[el.name] = el.value;
      }
    });

    Array.from(form.querySelectorAll('select')).forEach(function (el) {
      if (el.name) data[el.name] = el.value;
    });

    Array.from(form.querySelectorAll('textarea')).forEach(function (el) {
      if (el.name) data[el.name] = el.value;
    });

    BOOL_FIELDS.forEach(function (f) {
      if (typeof data[f] === 'undefined') data[f] = false;
    });

    return data;
  }

  function save() {
    var status = document.getElementById('th-save-status');
    status.className = 'th-save-status';
    status.textContent = 'Saving…';
    fetch(restBase() + 'settings', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
      body: JSON.stringify(payload())
    }).then(function (r) {
      if (!r.ok) throw new Error('HTTP ' + r.status);
      status.classList.add('th-save-status--ok');
      status.textContent = 'Saved';
    }).catch(function (err) {
      status.classList.add('th-save-status--err');
      status.textContent = 'Failed: ' + err.message;
    });
  }

  function setStatus(elId, msg, ok) {
    var el = document.getElementById(elId);
    if (!el) return;
    el.textContent = msg;
    el.style.color = ok ? '#006644' : '#de350b';
  }

  function testNotification(type, extraParams) {
    var body = Object.assign({ type: type }, extraParams || {});
    var statusId = 'th-test-' + type + '-status';
    setStatus(statusId, 'Sending…', true);
    fetch(restBase() + 'notifications/test', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
      body: JSON.stringify(body)
    }).then(function (r) {
      return r.json().then(function (j) {
        if (!r.ok) throw new Error(j.error || 'HTTP ' + r.status);
        setStatus(statusId, j.status === 'sent' ? 'Sent OK' : 'Server says: ' + j.status, j.status === 'sent');
      });
    }).catch(function (err) {
      setStatus(statusId, 'Failed: ' + err.message, false);
    });
  }

  function exportConfig() {
    window.location.href = restBase() + 'config/export';
  }

  function importConfig() {
    var fileInput = document.getElementById('th-import-file');
    var statusEl = document.getElementById('th-import-status');
    if (!fileInput || !fileInput.files || !fileInput.files[0]) {
      if (statusEl) { statusEl.textContent = 'Select a file first.'; statusEl.style.color = '#de350b'; }
      return;
    }
    var reader = new FileReader();
    reader.onload = function (e) {
      var json;
      try { json = JSON.parse(e.target.result); }
      catch (parseErr) {
        if (statusEl) { statusEl.textContent = 'Invalid JSON: ' + parseErr.message; statusEl.style.color = '#de350b'; }
        return;
      }
      if (statusEl) { statusEl.textContent = 'Importing…'; statusEl.style.color = '#172b4d'; }
      fetch(restBase() + 'config/import', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
        body: JSON.stringify(json)
      }).then(function (r) {
        return r.json().then(function (j) {
          if (!r.ok) throw new Error(j.error || 'HTTP ' + r.status);
          if (statusEl) { statusEl.textContent = 'Imported. Reload to verify.'; statusEl.style.color = '#006644'; }
        });
      }).catch(function (err) {
        if (statusEl) { statusEl.textContent = 'Failed: ' + err.message; statusEl.style.color = '#de350b'; }
      });
    };
    reader.readAsText(fileInput.files[0]);
  }

  function allowlistAdd() {
    var fp  = (document.getElementById('th-al-fingerprint') || {}).value || '';
    var det = (document.getElementById('th-al-detector')    || {}).value || '';
    var red = (document.getElementById('th-al-redacted')    || {}).value || '';
    var rsn = (document.getElementById('th-al-reason')      || {}).value || '';
    var st  = document.getElementById('th-al-status');
    if (!fp.trim()) { if (st) { st.textContent = 'Fingerprint is required.'; st.style.color = '#de350b'; } return; }
    fetch(restBase() + 'allowlist', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
      body: JSON.stringify({ fingerprint: fp.trim(), detectorName: det, redacted: red, reason: rsn })
    }).then(function (r) {
      if (!r.ok) throw new Error('HTTP ' + r.status);
      if (st) { st.textContent = 'Added. Reload to see updated table.'; st.style.color = '#006644'; }
    }).catch(function (e) {
      if (st) { st.textContent = 'Failed: ' + e.message; st.style.color = '#de350b'; }
    });
  }

  function allowlistRemove(id, row) {
    fetch(restBase() + 'allowlist/' + id, { method: 'DELETE' })
      .then(function (r) { if (!r.ok) throw new Error('HTTP ' + r.status); row.remove(); })
      .catch(function (e) { alert('Remove failed: ' + e.message); });
  }

  document.addEventListener('DOMContentLoaded', function () {
    var btn = document.getElementById('th-save-settings');
    if (btn) btn.addEventListener('click', save);

    var addBtn = document.getElementById('th-al-add');
    if (addBtn) addBtn.addEventListener('click', allowlistAdd);

    document.querySelectorAll('.th-al-remove').forEach(function (b) {
      b.addEventListener('click', function () {
        var row = b.closest('tr');
        var id  = row ? row.getAttribute('data-al-id') : null;
        if (id) allowlistRemove(id, row);
      });
    });

    // Test email
    var testEmailBtn = document.getElementById('th-test-email-btn');
    if (testEmailBtn) {
      testEmailBtn.addEventListener('click', function () {
        var to = (document.getElementById('th-test-email-to') || {}).value || '';
        if (!to.trim()) { setStatus('th-test-email-status', 'Enter an email address.', false); return; }
        testNotification('email', { to: to.trim() });
      });
    }

    // Test Teams webhook
    var testTeamsBtn = document.getElementById('th-test-teams-btn');
    if (testTeamsBtn) {
      testTeamsBtn.addEventListener('click', function () {
        testNotification('teams', {});
      });
    }

    // Test Slack webhook
    var testSlackBtn = document.getElementById('th-test-slack-btn');
    if (testSlackBtn) {
      testSlackBtn.addEventListener('click', function () {
        testNotification('slack', {});
      });
    }

    // Export
    var exportBtn = document.getElementById('th-export-btn');
    if (exportBtn) {
      exportBtn.addEventListener('click', function (e) {
        e.preventDefault();
        exportConfig();
      });
    }

    // Import
    var importBtn = document.getElementById('th-import-btn');
    if (importBtn) {
      importBtn.addEventListener('click', importConfig);
    }
  });
})();
