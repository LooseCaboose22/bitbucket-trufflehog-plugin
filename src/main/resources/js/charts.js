(function (window) {
  'use strict';

  // Chart.js-backed chart helpers. Public API is identical to the previous
  // SVG implementation so th-dashboard.js requires no changes.

  function createCanvas(target, height) {
    target.innerHTML = '';
    var wrap = document.createElement('div');
    wrap.style.position = 'relative';
    wrap.style.height   = (height || 220) + 'px';
    wrap.style.width    = '100%';
    var canvas = document.createElement('canvas');
    wrap.appendChild(canvas);
    target.appendChild(wrap);
    return canvas;
  }

  function empty(target) {
    target.innerHTML = '<p class="th-empty">No data yet.</p>';
  }

  function hexToRgb(hex) {
    var m = /^#?([0-9a-f]{2})([0-9a-f]{2})([0-9a-f]{2})$/i.exec(hex);
    return m ? parseInt(m[1], 16) + ',' + parseInt(m[2], 16) + ',' + parseInt(m[3], 16) : null;
  }

  var GRID_COLOR  = 'rgba(9,30,66,0.06)';
  var TICK_COLOR  = '#6b778c';
  var LABEL_COLOR = '#172b4d';
  var FONT_SMALL  = { size: 11 };
  var FONT_NORMAL = { size: 12 };

  function donut(target, segments) {
    if (!segments || !segments.length) { empty(target); return; }
    var total = segments.reduce(function (a, s) { return a + s.value; }, 0);
    if (!total) { empty(target); return; }
    var canvas = createCanvas(target, 220);
    new Chart(canvas, {
      type: 'doughnut',
      data: {
        labels: segments.map(function (s) { return s.label; }),
        datasets: [{
          data:            segments.map(function (s) { return s.value; }),
          backgroundColor: segments.map(function (s) { return s.color; }),
          borderColor:     '#ffffff',
          borderWidth:     2,
          hoverOffset:     5
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        cutout: '64%',
        plugins: {
          legend: {
            position: 'right',
            labels: { boxWidth: 11, padding: 10, font: FONT_NORMAL, color: LABEL_COLOR }
          },
          tooltip: {
            callbacks: {
              label: function (ctx) {
                var pct = Math.round(ctx.parsed / total * 100);
                return '  ' + ctx.label + ': ' + ctx.parsed + '  (' + pct + '%)';
              }
            }
          }
        }
      }
    });
  }

  function bars(target, items) {
    if (!items || !items.length) { empty(target); return; }
    var h = Math.max(180, items.length * 28 + 20);
    var canvas = createCanvas(target, h);
    new Chart(canvas, {
      type: 'bar',
      data: {
        labels: items.map(function (it) { return it.label; }),
        datasets: [{
          data: items.map(function (it) { return it.value; }),
          backgroundColor: items.map(function (it) {
            var rgb = hexToRgb(it.color || '#0052cc');
            return rgb ? 'rgba(' + rgb + ',0.85)' : 'rgba(0,82,204,0.85)';
          }),
          borderRadius:  3,
          borderSkipped: false,
          barThickness:  16
        }]
      },
      options: {
        indexAxis: 'y',
        responsive: true,
        maintainAspectRatio: false,
        plugins: { legend: { display: false } },
        scales: {
          x: {
            grid:  { color: GRID_COLOR },
            ticks: { color: TICK_COLOR, font: FONT_SMALL, precision: 0 }
          },
          y: {
            grid:  { display: false },
            ticks: { color: LABEL_COLOR, font: FONT_NORMAL }
          }
        }
      }
    });
  }

  function area(target, items) {
    if (!items || !items.length) { empty(target); return; }
    var canvas = createCanvas(target, 200);
    new Chart(canvas, {
      type: 'line',
      data: {
        labels: items.map(function (it) { return it.label; }),
        datasets: [{
          data:                 items.map(function (it) { return it.value; }),
          borderColor:          '#0052cc',
          backgroundColor:      'rgba(0,82,204,0.10)',
          fill:                 true,
          tension:              0.35,
          pointRadius:          3,
          pointHoverRadius:     5,
          pointBackgroundColor: '#0052cc',
          borderWidth:          2
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: { legend: { display: false } },
        scales: {
          x: {
            grid:  { display: false },
            ticks: { color: TICK_COLOR, font: FONT_SMALL, maxTicksLimit: 8 }
          },
          y: {
            beginAtZero: true,
            grid:        { color: GRID_COLOR },
            ticks:       { color: TICK_COLOR, font: FONT_SMALL, precision: 0 }
          }
        }
      }
    });
  }

  function multiLineArea(target, seriesList) {
    var series = (seriesList || []).filter(function (s) { return s.points && s.points.length; });
    if (!series.length) { empty(target); return; }

    var labelsMap = {};
    series.forEach(function (s) {
      s.points.forEach(function (p) { labelsMap[p.label] = true; });
    });
    var labels = Object.keys(labelsMap).sort();

    var canvas = createCanvas(target, 210);
    new Chart(canvas, {
      type: 'line',
      data: {
        labels: labels,
        datasets: series.map(function (s) {
          var color = s.color || '#0052cc';
          var rgb   = hexToRgb(color);
          return {
            label:                s.name,
            data: labels.map(function (label) {
              var p = s.points.find(function (pt) { return pt.label === label; });
              return p ? p.value : 0;
            }),
            borderColor:          color,
            backgroundColor:      rgb ? 'rgba(' + rgb + ',0.08)' : 'transparent',
            fill:                 true,
            tension:              0.35,
            pointRadius:          3,
            pointHoverRadius:     5,
            pointBackgroundColor: color,
            borderWidth:          2
          };
        })
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        interaction: { mode: 'index', intersect: false },
        plugins: {
          legend: {
            position: 'bottom',
            labels: { boxWidth: 10, padding: 10, font: FONT_NORMAL, color: LABEL_COLOR }
          }
        },
        scales: {
          x: {
            grid:  { display: false },
            ticks: { color: TICK_COLOR, font: FONT_SMALL, maxTicksLimit: 8 }
          },
          y: {
            beginAtZero: true,
            grid:        { color: GRID_COLOR },
            ticks:       { color: TICK_COLOR, font: FONT_SMALL, precision: 0 }
          }
        }
      }
    });
  }

  window.THCharts = { donut: donut, bars: bars, area: area, multiLineArea: multiLineArea };
})(window);
