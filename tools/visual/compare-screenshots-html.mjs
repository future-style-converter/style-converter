// compare-screenshots-html.mjs
//
// HTML report renderer for compare-screenshots.mjs. Extracted so the main
// pipeline file stays under the per-file size budget (CLAUDE.md hard rule
// — split when >300 lines per file; the comparator was crossing 1100
// after the Section 5 / item 9 expansion).
//
// All exports are pure string-builders — no I/O, no side effects.
// Inputs: the `rows` array produced by analyzeComponent + the `opts`
// bundle constructed in main(). Output: a complete HTML document string.
//
// COMPARE_METRICS Section 5 — divergence-classifier badges, expanded
// metrics rows, per-channel SSIM <details>, and the report-wide legend
// all live here.

import { worstLabel, badgeColor } from './classify-divergence.mjs';
import { classifyTextProbe, textProbeBadgeColor } from './classify-text-probe.mjs';

// PLATFORMS list is duplicated here intentionally — the renderer needs
// to iterate the same fixed three-platform tuple as the pipeline. Importing
// it from compare-screenshots.mjs would create a circular import.
const PLATFORMS = ['iOS', 'Android', 'web'];

// ─────────────────────────────────────────────────────────────────────────────
// Entry point
// ─────────────────────────────────────────────────────────────────────────────

export function renderHTML(rows, opts) {
  // "Identical" threshold: SSIM >= 0.97 across every pair. SSIM is
  // perceptually-grounded and handles font AA naturally. Raw pixel diffs
  // are dominated by subpixel AA differences and aren't a useful summary
  // metric, but they're still shown per-pair for drill-down.
  const isIdentical = (r) =>
    Object.values(r.pairs)
      .filter(Boolean)
      .every((p) => p.ssim !== null && p.ssim >= 0.97);

  const totals = {
    components: rows.length,
    regressions: opts.useBaseline
      ? rows.filter((r) => r.baseline?.regressed).length
      : 0,
    identical: rows.filter(isIdentical).length,
  };

  const rowsHtml = rows.map((r) => renderRow(r, opts)).join('\n');

  // Stash a minimum-SSIM + searchable name on each row's DOM node so the
  // client-side sort + filter can work without re-parsing metrics.
  const labelHtml = opts.inputLabel
    ? ` <span class="input-label">(${escape(opts.inputLabel)})</span>`
    : '';

  // COMPARE_METRICS Section 5 — divergence-label legend at the top of
  // the report. Keeps every label visible up-front so a reviewer doesn't
  // have to scroll the spec to decode a badge colour. Rendered as a
  // sibling row in the sticky header so it stays on-screen while scrolling.
  // `no-content` (round 91) is appended after the canonical 7 labels so
  // the original reading order is preserved; `test-not-applicable` (FIX-E)
  // is appended LAST as the trailing dark-slate chip — same rationale
  // (preserve the canonical reading order while keeping the new labels
  // visually distinct at the end of the row). `glyph-metric-noise`
  // (swarm-002) slots BETWEEN sub-pixel-noise and color-drift to mirror
  // its severity rank (just above sub-pixel-noise, just below the
  // divergence-bucket yellows) so the legend's left-to-right ordering
  // reads as "best → worst".
  const legendItems = [
    'identical', 'sub-pixel-noise', 'glyph-metric-noise',
    'color-drift', 'edge-shift', 'structural-divergence', 'mixed',
    'unknown', 'no-content', 'test-not-applicable',
  ];
  const legendHtml = legendItems
    .map((l) => `<span class="badge divergence" style="background:${badgeColor(l)}">${l}</span>`)
    .join(' ');

  return `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Style Converter — cross-platform capture report</title>
<style>
${BASE_CSS}
</style>
</head>
<body>
<header class="top">
  <h1>Cross-platform capture report${labelHtml}</h1>
  <p class="meta">
    ${totals.components} components · ${totals.identical} identical (SSIM &ge; 0.97)
    ${opts.useBaseline ? ` · <span class="${totals.regressions > 0 ? 'bad' : 'ok'}">${totals.regressions} regressions</span>` : ''}
    · SSIM threshold ${opts.ssimThreshold} · pixel threshold ${opts.pixelThreshold}%
  </p>
  <p class="meta legend">Divergence labels: ${legendHtml}</p>
  <!-- Metric caveats, stated where the numbers are actually read. Both are
       properties of ssim.js's defaults, not of this harness's inputs, and
       both cause honest misreadings of the column beside them. -->
  <p class="meta caveat">
    ⚠ <b>Reading SSIM:</b> it is computed on <b>luminance only</b> (Rec.601
    grayscale), so a hue change at matched luminance scores ~1.0 — check the
    ΔE column for colour. It is also <b>downsampled by <code>round(min(W,H)/256)</code></b>,
    so tall components are scored at half resolution while pixelmatch and ΔE
    stay at 1× — SSIM is <b>not comparable across components of different heights</b>.
  </p>
  <p class="meta">
    Generated ${new Date().toLocaleString()}
  </p>
  <div class="controls">
    <input type="search" id="search" placeholder="Filter components by name…" autocomplete="off">
    <label><input type="checkbox" id="onlyDiff"> diffs only</label>
    <label for="sort">sort:</label>
    <select id="sort">
      <option value="index">by index (default)</option>
      <option value="worst">worst SSIM first</option>
      <option value="best">best SSIM first</option>
      <option value="name">by name</option>
    </select>
    <span class="visible-count" id="visibleCount"></span>
  </div>
</header>

${renderTypographyProbes(opts.perPlatformProbes)}

<main id="rows">
${rowsHtml}
</main>

<script>
(() => {
  const rowsEl = document.getElementById('rows');
  const rows = Array.from(rowsEl.querySelectorAll('.row'));
  const search = document.getElementById('search');
  const onlyDiff = document.getElementById('onlyDiff');
  const sortSel = document.getElementById('sort');
  const countEl = document.getElementById('visibleCount');

  function minSsim(r) {
    // Data attribute written server-side; parseFloat never returns NaN for
    // valid values, and falls back to 1 so rows missing the attr stay at
    // the "best" end of sorts.
    const v = parseFloat(r.getAttribute('data-min-ssim'));
    return Number.isFinite(v) ? v : 1;
  }

  function apply() {
    const q = (search.value || '').toLowerCase().trim();
    const diffsOnly = onlyDiff.checked;
    const sortBy = sortSel.value;

    // Sort (detach + reattach children — O(n) vs O(n²) for reorder loops).
    const ordered = [...rows];
    if (sortBy === 'worst') ordered.sort((a, b) => minSsim(a) - minSsim(b));
    else if (sortBy === 'best') ordered.sort((a, b) => minSsim(b) - minSsim(a));
    else if (sortBy === 'name') ordered.sort((a, b) =>
      a.dataset.name.localeCompare(b.dataset.name));
    else ordered.sort((a, b) =>
      Number(a.dataset.index) - Number(b.dataset.index));

    const frag = document.createDocumentFragment();
    let visible = 0;
    for (const r of ordered) {
      const matches = !q || r.dataset.name.toLowerCase().includes(q);
      const diffOk = !diffsOnly || r.classList.contains('has-diff') || r.classList.contains('regressed');
      if (matches && diffOk) {
        r.style.display = '';
        visible++;
      } else {
        r.style.display = 'none';
      }
      frag.appendChild(r);
    }
    rowsEl.appendChild(frag);
    countEl.textContent = visible === rows.length
      ? \`\${rows.length} components\`
      : \`\${visible} / \${rows.length} components\`;
  }

  search.addEventListener('input', apply);
  onlyDiff.addEventListener('change', apply);
  sortSel.addEventListener('change', apply);
  apply();

  // Keyboard: '/' focuses search — standard convention.
  document.addEventListener('keydown', (e) => {
    if (e.key === '/' && document.activeElement !== search) {
      e.preventDefault();
      search.focus();
      search.select();
    }
  });
})();
</script>
</body>
</html>`;
}

// ─────────────────────────────────────────────────────────────────────────────
// Typography probes section (B-EXT spec Section 7 step 12)
// ─────────────────────────────────────────────────────────────────────────────
//
// Renders the perPlatformProbes block (B8/B9/B10) as a single section near
// the top of the report. Returns empty string when probes weren't run
// (probeBlock is null/undefined) so the existing report layout doesn't
// gain a "no data" bar that would only ever appear during the rollout.

function renderTypographyProbes(probeBlock) {
  // No probe data → render nothing. This is the v3-graceful behaviour
  // (Section 8 q8): the section disappears entirely when the probe
  // pipeline hasn't run, so existing baseline reports are unchanged.
  if (!probeBlock) return '';
  const b8 = probeBlock.b8_subpixelBaseline;
  const b9 = probeBlock.b9_aaStrategy;
  const b10 = probeBlock.b10_glyphSpacing;
  // Headline label across the three probes (Section 5 — `classifyTextProbe`).
  const label = classifyTextProbe(b8, b9, b10);
  const labelColor = textProbeBadgeColor(label);

  // Per-metric one-liners with the headline numeric.
  const b8Line = b8
    ? `B8 baseline drift: <strong>${b8.maxBaselineDeltaPx.toFixed(2)} px</strong> (max pair) · ${b8.divergentFixtureCount} fixture(s) > 0.5 px`
    : `B8 baseline: <em>no data</em>`;
  const b9Line = b9
    ? `B9 AA strategy: <strong>${escape(b9.agreement)}</strong> across platforms`
    : `B9 AA strategy: <em>no data</em>`;
  const b10Line = b10
    ? `B10 glyph spacing: <strong>mean Δ ${b10.maxMeanDeltaPx.toFixed(2)} px</strong> · stddev Δ ${b10.maxStddevDeltaPx.toFixed(2)} px`
    : `B10 glyph spacing: <em>no data</em>`;

  // Per-platform breakdown table — three columns × N fixtures. Hidden
  // inside <details> by default to keep the section compact.
  const detailRows = renderProbeBreakdown(b8, b9, b10);

  // Provenance warning — the three platforms do NOT produce the 4× probe
  // buffer the same way, and B8/B9/B10 are sub-pixel measurements, so the
  // difference is not cosmetic. iOS re-renders natively at
  // ImageRenderer.scale = 4.0 and web re-renders via
  // capture-screenshots-hires.mjs, but Android upscales an already-laid-out
  // 1× bitmap with Bitmap.createScaledBitmap(..., filter=true)
  // (ScreenshotManager.kt:401 — its own comment concedes the hinting loss).
  // Bilinear interpolation manufactures the intermediate samples that B8's
  // baseline-column scan, B9's AA-strategy FFT and B10's glyph-edge
  // projection are reading, so an Android probe row measures the resampler,
  // not the renderer. Say so where the numbers are read rather than
  // trusting anyone to remember it.
  const androidCaveat = `
    <p style="margin:8px 0 0; font-size:12px; line-height:1.5; opacity:.9;">
      ⚠ <strong>Android probe rows are not renderer-comparable.</strong>
      iOS and web re-render natively at 4×; Android bilinearly upscales a 1×
      bitmap (<code>ScreenshotManager.kt:401</code>). Any B8/B9/B10 pair
      involving Android is partly measuring that interpolation. Treat
      iOS↔web as the only sub-pixel-trustworthy pair until Android renders
      at true 4×.
    </p>`;

  return `
<section class="typography-probes" style="margin: 16px; padding: 16px; border: 1px solid #444; border-radius: 6px; background: #1f1f2a; color: #eee;">
  <header style="display:flex; align-items:center; gap:12px; margin-bottom:8px;">
    <h2 style="margin:0; font-size:14px;">Typography probes</h2>
    <span class="badge text-probe" style="background:${labelColor}; padding:2px 8px; border-radius:4px; font-size:11px; color:white;">${escape(label)}</span>
    <span style="opacity:0.7; font-size:11px;">B8 / B9 / B10 — perceptual divergence; not gating CI</span>
  </header>
  <div style="display:grid; grid-template-columns: 1fr 1fr 1fr; gap:8px; font-size:12px;">
    <div>${b8Line}</div>
    <div>${b9Line}</div>
    <div>${b10Line}</div>
  </div>
  ${androidCaveat}
  <details style="margin-top:8px;">
    <summary style="cursor:pointer; font-size:12px; opacity:0.85;">Per-platform breakdown</summary>
    ${detailRows}
  </details>
</section>`;
}

function renderProbeBreakdown(b8, b9, b10) {
  // Build a flat <table>: one row per (metric × fixture) × per-platform value.
  // Cells empty when the platform didn't capture that fixture (web-only
  // first pass per the spec's Section 7 iOS/Android scaffold note).
  const rows = [];
  if (b8) {
    const fixtures = unionKeys(b8.perPlatform);
    for (const f of fixtures) {
      rows.push(`<tr><td>B8</td><td>${escape(f)}</td>` +
        ['iOS', 'Android', 'web'].map(p =>
          `<td>${b8.perPlatform[p][f] != null ? b8.perPlatform[p][f] : '—'}</td>`
        ).join('') + `</tr>`);
    }
  }
  if (b9) {
    const fixtures = unionKeys(b9.perPlatform);
    for (const f of fixtures) {
      rows.push(`<tr><td>B9</td><td>${escape(f)}</td>` +
        ['iOS', 'Android', 'web'].map(p =>
          `<td>${escape(b9.perPlatform[p][f] ?? '—')}</td>`
        ).join('') + `</tr>`);
    }
  }
  if (b10) {
    const fixtures = unionKeys(b10.perPlatform);
    for (const f of fixtures) {
      rows.push(`<tr><td>B10</td><td>${escape(f)}</td>` +
        ['iOS', 'Android', 'web'].map(p => {
          const v = b10.perPlatform[p][f];
          return `<td>${v ? `${v.mean}±${v.stddev}` : '—'}</td>`;
        }).join('') + `</tr>`);
    }
  }
  if (rows.length === 0) {
    return `<p style="font-size:11px; opacity:0.7;">No per-fixture data captured.</p>`;
  }
  return `<table style="margin-top:8px; font-size:11px; width:100%; border-collapse:collapse;">
    <thead><tr><th style="text-align:left;">Metric</th><th style="text-align:left;">Fixture</th><th>iOS</th><th>Android</th><th>web</th></tr></thead>
    <tbody>${rows.join('')}</tbody>
  </table>`;
}

function unionKeys(perPlatform) {
  // Helper: union of fixture names across the three per-platform maps.
  // Inlined here (not in classify-text-probe.mjs) because it's HTML-render
  // scaffolding, not classification logic.
  const set = new Set();
  for (const p of ['iOS', 'Android', 'web']) {
    for (const k of Object.keys(perPlatform?.[p] ?? {})) set.add(k);
  }
  return [...set];
}

// ─────────────────────────────────────────────────────────────────────────────
// Per-row + per-pair renderers
// ─────────────────────────────────────────────────────────────────────────────

function renderRow(r, opts) {
  const dimsOk = PLATFORMS
    .map((p) => r.platforms[p])
    .filter((pp) => pp.present)
    .every((pp, _, arr) => pp.width === arr[0].width && pp.height === arr[0].height);

  // A row is marked "has-diff" when any pair drops below the perceptual
  // threshold — in practice this means a structural change (missing shadow,
  // wrong shape, mis-applied color), not just font AA differences.
  const hasDiff = Object.values(r.pairs).some(
    (p) => p && p.ssim !== null && p.ssim < 0.90
  );

  // Lowest SSIM across every pair. Used by the client-side "sort by worst"
  // option. 1.0 means perfect parity; 0.0 means "nothing in common".
  const ssims = Object.values(r.pairs)
    .filter((p) => p && p.ssim !== null)
    .map((p) => p.ssim);
  const minSsim = ssims.length ? Math.min(...ssims).toFixed(4) : '1';

  // Index from the filename prefix (NNN_Foo.png) — client uses it to
  // restore the default "index" sort order after filtering.
  const indexMatch = r.name.match(/^(\d+)_/);
  const index = indexMatch ? Number(indexMatch[1]) : 0;

  const cls = [hasDiff ? 'has-diff' : '', r.baseline?.regressed ? 'regressed' : ''].filter(Boolean).join(' ');

  // Section 5 — row-level "headline divergence" badge: pick the worst
  // divergence label across the row's three cross-platform pairs so the
  // h2 surfaces the most-severe pair without requiring a drill-down.
  // Severity ordering: structural > color-drift > edge-shift > mixed >
  // sub-pixel-noise > identical > unknown (defined in classify-divergence.mjs).
  const rowLabels = Object.values(r.pairs)
    .filter(Boolean)
    .map((p) => p.divergence);
  const headlineLabel = worstLabel(rowLabels);
  const headlineBadge = `<span class="badge divergence" style="background:${badgeColor(headlineLabel)}" title="worst divergence across pairs">${headlineLabel}</span>`;

  return `<section class="row ${cls}"
      data-has-diff="${hasDiff}"
      data-min-ssim="${minSsim}"
      data-index="${index}"
      data-name="${escape(r.name)}">
    <h2>
      ${escape(r.name)}
      ${headlineBadge}
      <span class="min-ssim" title="lowest SSIM across pairs">SSIM ${Number(minSsim).toFixed(3)}</span>
      ${dimsOk ? '' : '<span class="badge warn">size mismatch</span>'}
      ${r.baseline?.regressed ? '<span class="badge bad">regressed</span>' : ''}
    </h2>

    <div class="platforms">
      ${PLATFORMS.map((p) => renderPlatformCell(p, r)).join('')}
    </div>

    <div class="pairs">
      ${renderPair('iOS ↔ Android', r.pairs['iOS-Android'])}
      ${renderPair('iOS ↔ Web',     r.pairs['iOS-web'])}
      ${renderPair('Android ↔ Web', r.pairs['Android-web'])}
    </div>

    ${opts.useBaseline ? renderBaseline(r.baseline) : ''}
  </section>`;
}

function renderPlatformCell(p, r) {
  const pp = r.platforms[p];
  if (pp.error) {
    return `<div class="platform errored">
      <div class="plabel">${p} <span class="bad">decode error</span></div>
      <div class="pimg muted err">${escape(pp.error)}</div>
    </div>`;
  }
  if (!pp.present) {
    return `<div class="platform missing"><div class="plabel">${p}</div><div class="pimg muted">missing</div></div>`;
  }
  // loading="lazy" keeps the browser from decoding 600+ images on report
  // open — cheap win for large reports.
  return `<div class="platform">
    <div class="plabel">${p} <span class="dim">${pp.width}×${pp.height}</span></div>
    <img src="${pp.image}" alt="${p} capture" loading="lazy">
  </div>`;
}

function renderPair(title, pair) {
  if (!pair) return `<div class="pair missing"><div class="ptitle">${title}</div><div class="pimg muted">n/a</div></div>`;
  // Severity tiers keyed on SSIM since it's a perceptual metric:
  //   bad  (< 0.85) — clearly different, likely a rendering bug
  //   warn (< 0.97) — noticeable but may be font/AA noise
  //   ok   — perceptually identical
  const sev = pair.ssim === null ? 'warn'
           : pair.ssim < 0.85 ? 'bad'
           : pair.ssim < 0.97 ? 'warn'
           : 'ok';

  // Section 5 — per-pair classifier badge. Independently coloured from
  // the SSIM-based `sev` tier so a reviewer can see both the existing
  // "headline number" colour AND the new fingerprint label.
  const label = pair.divergence ?? 'unknown';
  const labelBadge = `<span class="badge divergence" style="background:${badgeColor(label)}">${label}</span>`;

  // Section 5 — expanded metrics row. `?.` chains keep this resilient to
  // any optional metric being null (Section 2 / Section 6 backward-compat).
  // Layout: each metric is one inline span; missing values render as "—".
  // Compact format per spec example:
  //   `dssim 0.010 · edge 1.00 · ΔE p95 0 · pHash 0 · KL r0.013 g0.009 b0.006`
  const fmt = (v, dp = 2) => (v == null ? '—' : Number(v).toFixed(dp));
  const dssimStr = fmt(pair.dssim, 4);
  const edgeStr = fmt(pair.edgeSsim, 2);
  const labP95Str = fmt(pair.labDeltaE?.p95, 2);
  const labMaxStr = fmt(pair.labDeltaE?.max, 2);
  const pHashStr = pair.pHash?.hammingDistance ?? '—';
  const klR = fmt(pair.histogramKL?.r, 3);
  const klG = fmt(pair.histogramKL?.g, 3);
  const klB = fmt(pair.histogramKL?.b, 3);
  const extraMetrics = `
      <div class="metrics extra">
        <span title="DSSIM = (1-SSIM)/2">dssim <b>${dssimStr}</b></span>
        <span title="Sobel-3 edge-map SSIM">edge <b>${edgeStr}</b></span>
        <span title="CIEDE2000 ΔE — p95 / max">ΔE p95 <b>${labP95Str}</b> · max <b>${labMaxStr}</b></span>
        <span title="pHash 64-bit Hamming distance (0..64)">pHash <b>${pHashStr}</b></span>
        <span title="Histogram KL divergence per channel">KL r<b>${klR}</b> g<b>${klG}</b> b<b>${klB}</b></span>
      </div>`;

  // Section 5 — collapsed `<details>` for per-channel SSIM so the row
  // stays compact by default. Per-channel only renders when the metric
  // is present (Section 2 — every new field optional).
  const pcs = pair.perChannelSsim;
  const perChannelDetails = pcs
    ? `<details class="per-channel">
        <summary>per-channel SSIM</summary>
        <div class="metrics">
          <span>R <b>${fmt(pcs.r, 4)}</b></span>
          <span>G <b>${fmt(pcs.g, 4)}</b></span>
          <span>B <b>${fmt(pcs.b, 4)}</b></span>
          <span>A <b>${fmt(pcs.a, 4)}</b></span>
        </div>
      </details>`
    : '';

  return `<div class="pair ${sev}">
    <div class="ptitle">${title} ${labelBadge}</div>
    <div class="metrics">
      <span>SSIM <b>${pair.ssim?.toFixed(4) ?? '—'}</b></span>
      <span>Δpx <b>${pair.pixelMismatchedPct.toFixed(2)}%</b></span>
    </div>
    ${extraMetrics}
    ${perChannelDetails}
    <img src="${pair.diffImage}" alt="${title} diff" loading="lazy">
  </div>`;
}

function renderBaseline(baseline) {
  if (!baseline) return '';
  const cells = PLATFORMS.map((p) => {
    const b = baseline.platforms[p];
    if (!b?.present) return `<div class="pair missing"><div class="ptitle">${p} vs baseline</div><div class="pimg muted">n/a</div></div>`;
    const sev = b.regressed ? 'bad' : b.pixelMismatchedPct > 0.5 ? 'warn' : 'ok';
    return `<div class="pair ${sev}">
      <div class="ptitle">${p} vs baseline ${b.regressed ? '⚠️' : ''}</div>
      <div class="metrics">
        <span>SSIM <b>${b.ssim?.toFixed(4) ?? '—'}</b></span>
        <span>Δpx <b>${b.pixelMismatchedPct.toFixed(2)}%</b></span>
      </div>
      <img src="${b.diffImage}" alt="${p} baseline diff" loading="lazy">
    </div>`;
  }).join('');
  return `<details class="baseline" ${baseline.regressed ? 'open' : ''}>
    <summary>Baseline comparison${baseline.regressed ? ' — REGRESSIONS' : ''}</summary>
    <div class="pairs">${cells}</div>
  </details>`;
}

// HTML-escape per the standard 5-character entity set. Used everywhere a
// user-supplied string (filenames, error messages) is interpolated into
// the report so a stray `<script>` in an error message can't run.
export function escape(s) {
  return s.replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

// ─────────────────────────────────────────────────────────────────────────────
// Stylesheet
// ─────────────────────────────────────────────────────────────────────────────

const BASE_CSS = `
* { box-sizing: border-box; }
body {
  margin: 0;
  font-family: -apple-system, system-ui, sans-serif;
  background: #111;
  color: #eee;
  font-size: 14px;
}
.top {
  position: sticky;
  top: 0;
  z-index: 10;
  padding: 16px 24px;
  background: #1a1a2e;
  border-bottom: 1px solid #333;
}
.top h1 { margin: 0 0 4px 0; font-size: 18px; }
.top h1 .input-label { font-weight: 400; color: #888; font-size: 13px; font-family: ui-monospace, monospace; }
.top .meta { margin: 2px 0; color: #999; font-size: 13px; }

.controls {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-top: 8px;
}
.controls input[type="search"] {
  flex: 1;
  min-width: 200px;
  max-width: 360px;
  padding: 6px 10px;
  background: rgba(255,255,255,0.05);
  border: 1px solid rgba(255,255,255,0.15);
  border-radius: 4px;
  color: #fff;
  font-size: 13px;
  outline: none;
}
.controls input[type="search"]:focus { border-color: #68a; }
.controls label { color: #aaa; font-size: 13px; cursor: pointer; user-select: none; }
.controls select {
  background: rgba(255,255,255,0.05);
  border: 1px solid rgba(255,255,255,0.15);
  border-radius: 4px;
  color: #fff;
  padding: 5px 8px;
  font-size: 13px;
}
.visible-count { color: #888; font-size: 12px; margin-left: auto; }

main { padding: 16px 24px; }

/* Narrow viewports: stack platform / pair grids instead of horizontal scroll */
@media (max-width: 900px) {
  .platforms, .pairs { grid-template-columns: 1fr !important; }
  main { padding: 12px; }
  .top { padding: 12px; }
  .controls input[type="search"] { max-width: none; }
}

.row {
  margin-bottom: 24px;
  padding: 16px;
  background: #181824;
  border: 1px solid #2a2a3a;
  border-radius: 8px;
}
.row.has-diff { border-color: #663; }
.row.regressed { border-color: #a33; background: #2a1818; }
.row { overflow: hidden; } /* prevent inner overflow bubbling to body */

.row h2 {
  margin: 0 0 12px 0;
  font-size: 14px;
  font-family: ui-monospace, monospace;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 8px;
}
.badge {
  font-family: -apple-system, system-ui, sans-serif;
  font-size: 11px;
  padding: 2px 6px;
  border-radius: 3px;
  font-weight: 500;
}
.badge.warn { background: #664; color: #fe9; }
.badge.bad  { background: #622; color: #fcc; }
/* Section 5 — divergence-label badges. Background colour is supplied
   inline by badgeColor() so the palette lives in JS (single source of
   truth shared with the legend). Foreground stays white for contrast
   against every palette colour. */
.badge.divergence {
  color: #fff;
  font-family: ui-monospace, monospace;
  letter-spacing: 0.02em;
  font-size: 10px;
  padding: 2px 6px;
}
.top .legend { display: flex; flex-wrap: wrap; align-items: center; gap: 4px; }
.top .legend .badge.divergence { font-size: 10px; }
/* Metric caveats — visible enough to be read before the numbers are
   trusted, quiet enough not to compete with the headline counts. */
.top .caveat { max-width: 90ch; line-height: 1.5; opacity: .85; }
.top .caveat code { font-size: 11px; }

.min-ssim {
  font-family: -apple-system, system-ui, sans-serif;
  font-size: 11px;
  color: #888;
  font-weight: 400;
  margin-left: auto;
}

.platforms { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; margin-bottom: 12px; }
.platform { background: #0e0e18; padding: 8px; border-radius: 4px; }
.platform .plabel { font-size: 12px; color: #ccc; margin-bottom: 6px; font-family: ui-monospace, monospace; }
.platform .plabel .dim { color: #666; margin-left: 4px; }
.platform img { display: block; width: 100%; max-width: 100%; height: auto; image-rendering: pixelated; }
.platform.missing .pimg.muted { color: #555; text-align: center; padding: 32px 0; font-style: italic; }
.platform.errored { border: 1px solid #a44; }
.platform.errored .pimg.err { color: #f99; padding: 16px; font-family: ui-monospace, monospace; font-size: 11px; word-break: break-word; }

.pairs { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; }
.pair {
  background: #0e0e18;
  padding: 8px;
  border-radius: 4px;
  border-left: 3px solid transparent;
}
.pair.ok   { border-left-color: #4a4; }
.pair.warn { border-left-color: #aa4; }
.pair.bad  { border-left-color: #a44; }
.pair .ptitle { font-size: 12px; color: #ccc; margin-bottom: 6px; font-family: ui-monospace, monospace; }
.pair .metrics { font-size: 11px; color: #aaa; margin-bottom: 6px; display: flex; gap: 12px; flex-wrap: wrap; }
.pair .metrics b { color: #fff; font-weight: 500; }
/* Section 5 — extra metrics row sits below the SSIM/Δpx headline. Uses
   a slightly muted colour so it doesn't compete visually with the
   primary metrics. flex-wrap so it never overflows narrow viewports. */
.pair .metrics.extra { font-size: 10px; color: #888; gap: 10px; margin-bottom: 6px; }
.pair .metrics.extra b { color: #ddd; }
.pair .per-channel { font-size: 10px; color: #888; margin-bottom: 6px; }
.pair .per-channel summary { cursor: pointer; }
.pair .per-channel .metrics { margin-top: 4px; gap: 8px; }
.pair img { display: block; width: 100%; max-width: 100%; height: auto; image-rendering: pixelated; }
.pair.missing .pimg.muted { color: #555; text-align: center; padding: 32px 0; font-style: italic; }

.baseline {
  margin-top: 12px;
  padding: 8px 12px;
  background: #12121c;
  border-radius: 4px;
}
.baseline summary { cursor: pointer; color: #aaa; font-size: 12px; }
.baseline[open] summary { margin-bottom: 8px; }

.ok  { color: #6c6; }
.bad { color: #f99; }

/* (Client-side JS handles diff-only filtering via row.style.display now —
   the old CSS-based .filter-diffs toggle is no longer used.) */
`;
