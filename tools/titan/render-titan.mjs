#!/usr/bin/env node
//
// tools/titan/render-titan.mjs
//
// TITAN dashboard generator — reads a TITAN manifest (manifestVersion 4)
// and produces a single self-contained HTML file at
// `tools/visual/report/titan.html` that humans can open without a build step
// or external CDN dependency.
//
// Companion to (do NOT replace) the legacy 327-pair report at
// `tools/visual/report/index.html` rendered by `compare-screenshots-html.mjs`.
// That report was built for a few hundred fixtures with thumbnail diffs;
// the TITAN corpus is ~10k tests, so this dashboard is summary-first
// (heatmap + chips + drill-down lists) instead of image-grid-first.
//
// Spec: `docs/reports/TITAN_ARCHITECTURE.md` Section 7.3.
//
// Usage:
//     node tools/titan/render-titan.mjs                   # latest run
//     node tools/titan/render-titan.mjs --run swarm-2b-w4 # explicit
//     node tools/titan/render-titan.mjs --out path/to.html
//
// Pure helpers exported for unit tests:
//     buildWptSourceUrl, labelOrder, aggregateManifest, escapeHtml

import { promises as fs } from 'node:fs';
import { existsSync, statSync, readdirSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import { badgeColor } from '../visual/classify-divergence.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = path.resolve(__dirname, '..', '..');

// ── Pure helpers (exported for unit tests) ──────────────────────────────────

// Canonical label order for legend / heatmap columns. Matches
// classify-divergence.mjs severity ranking + adds `no-data` (used by
// inject-wpt-block when an entire test produced no comparable output —
// distinct from `no-content` which is per-pair empty foreground).
// Order: best to worst, then the three "pipeline failure" labels at the
// end so they read as a separate cluster in the heatmap.
export function labelOrder() {
  return [
    'identical',
    'sub-pixel-noise',
    'color-drift',
    'edge-shift',
    'mixed',
    'structural-divergence',
    'no-content',
    'test-not-applicable',
    'no-data',
    'unknown',
  ];
}

// Extra label color for `no-data` (not in classify-divergence.mjs because
// it's a manifest-level label, not a per-pair classifier output). Picked
// distinct from every existing palette entry: dark warm-grey reads as
// "absent" rather than "broken" or "out of scope".
export function titanBadgeColor(label) {
  if (label === 'no-data') return '#3a3530';
  return badgeColor(label);
}

// HTML-escape per the standard 5-character entity set. Identical to the
// helper in compare-screenshots-html.mjs; duplicated here because that
// module is a CommonJS-style string-builder we do not want to import (it
// pulls in ssim/sharp transitively for its own neighbours).
export function escapeHtml(s) {
  if (s == null) return '';
  return String(s).replace(/[&<>"']/g, (c) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
  }[c]));
}

// Build a github.com URL for a WPT test path (used by the per-test row
// "src" link). The TITAN manifest stores test paths as
// `css/css-grid/grid-area-005.html`. We pin to the manifest's WPT ref
// (the SHA we captured against) so the link always resolves to the
// exact source the dashboard was built from — re-pinning the corpus
// next quarter doesn't break old dashboards.
//
// Public for unit testing — pure function, no I/O.
export function buildWptSourceUrl(testPath, wptRef) {
  if (!testPath) return null;
  // wpt repo layout: top-level `css/` is under the repo root, so the
  // testPath is the path component verbatim. ref defaults to "master"
  // when missing (older manifests pre-FIX-E sometimes lack it).
  const ref = wptRef && /^[0-9a-f]{7,40}$/i.test(wptRef) ? wptRef : 'master';
  // encodeURI (not encodeURIComponent) so the slashes are preserved as
  // path separators — github.com/.../blob/<ref>/<path>.
  const encodedPath = testPath.split('/').map(encodeURIComponent).join('/');
  return `https://github.com/web-platform-tests/wpt/blob/${ref}/${encodedPath}`;
}

// Aggregate a TITAN manifest into the headline numbers + per-section
// histograms the dashboard renders. Pure function — input is the parsed
// JSON object, output is a plain JS object the renderer turns into HTML.
//
// `bucketsIdx` is the optional `wpt-buckets.json` notApplicable map
// (testRel → [tag, …]). When provided, tests whose manifest lacks an
// inline `notApplicableTags` field get backfilled — this lets the
// dashboard surface FIX-E reclassifications even on manifests captured
// before the inject-wpt-block.mjs notApplicableTags field landed.
export function aggregateManifest(manifest, bucketsIdx = null) {
  if (!manifest || !manifest.wpt || !manifest.wpt.results) {
    return {
      totalTests: 0,
      labelHistogram: {},
      bySection: {},
      tagHistogram: {},
      bucketBreakdown: {},
      sectionList: [],
      labelList: labelOrder(),
      wptRef: null,
      runId: null,
      generatedAt: null,
      duration: null,
    };
  }

  const wpt = manifest.wpt;
  const labelList = labelOrder();
  const labelHistogram = Object.fromEntries(labelList.map((l) => [l, 0]));
  const bySection = {};
  const tagHistogram = {};
  const bucketBreakdown = {};

  // Walk every test exactly once: maintain three rolling tallies
  // (overall label histogram, per-section sub-histograms, tag cloud).
  // Bucket count comes from the manifest header but we recompute here
  // so the dashboard reflects the actual results map (which may differ
  // from `wpt.buckets` if a section was skipped post-bucket-assignment).
  for (const [testPath, t] of Object.entries(wpt.results)) {
    const label = t.divergence ?? 'unknown';
    if (labelHistogram[label] == null) labelHistogram[label] = 0;
    labelHistogram[label] += 1;

    const section = t.specSection || 'unknown';
    if (!bySection[section]) {
      bySection[section] = {
        section,
        total: 0,
        labels: Object.fromEntries(labelList.map((l) => [l, 0])),
        tests: [],
      };
    }
    const sec = bySection[section];
    sec.total += 1;
    if (sec.labels[label] == null) sec.labels[label] = 0;
    sec.labels[label] += 1;

    // Backfill notApplicableTags from the bucketer index when manifest
    // doesn't carry them inline (W4 was captured before inject-wpt-block
    // started writing the field per-test). Inline always wins so the
    // truth source stays the manifest when it has data.
    let naTags = Array.isArray(t.notApplicableTags) ? t.notApplicableTags : null;
    if ((!naTags || naTags.length === 0) && bucketsIdx) {
      const fromBuckets = bucketsIdx[testPath];
      if (Array.isArray(fromBuckets) && fromBuckets.length > 0) {
        naTags = fromBuckets;
      }
    }
    naTags = naTags || [];
    for (const tag of naTags) {
      tagHistogram[tag] = (tagHistogram[tag] || 0) + 1;
    }

    const bucket = t.bucket || 'unknown';
    bucketBreakdown[bucket] = (bucketBreakdown[bucket] || 0) + 1;

    sec.tests.push({
      path: testPath,
      label,
      bucket,
      naTags,
      // Headline metric: web-vs-ref ssim if present (Phase 1 captures
      // web-only browser-ref). Used for in-row sort-by-ssim later.
      ssim: t.browserRef?.diffs?.['web-ref']?.ssim ?? null,
      labP95: t.browserRef?.diffs?.['web-ref']?.labDeltaE?.p95 ?? null,
      pixelPct: t.browserRef?.diffs?.['web-ref']?.pixelMismatchedPct ?? null,
      // Phase-4 native-vs-ref pairs. Null means "platform not captured
      // this run" — the dashboard renders that as an explicit n/a cell,
      // so a missing platform is a visible gap, never a silent one.
      iosSsim: t.browserRef?.diffs?.['ios-ref']?.ssim ?? null,
      androidSsim: t.browserRef?.diffs?.['android-ref']?.ssim ?? null,
    });
  }

  // Sort sections alphabetically; tests within a section by descending
  // severity (worst first) so the drill-down surfaces actionable rows
  // at the top. Severity rank uses labelOrder index — the higher the
  // index in labelOrder() the worse, except `unknown` which we treat
  // as low severity (we don't know what's wrong, can't act on it).
  const severityRank = Object.fromEntries(
    labelList.map((l, i) => [l, l === 'unknown' ? -1 : i])
  );
  const sectionList = Object.values(bySection).sort((a, b) =>
    a.section.localeCompare(b.section)
  );
  for (const sec of sectionList) {
    sec.tests.sort((a, b) => {
      const sa = severityRank[a.label] ?? 0;
      const sb = severityRank[b.label] ?? 0;
      if (sa !== sb) return sb - sa;
      return a.path.localeCompare(b.path);
    });
  }

  return {
    totalTests: Object.keys(wpt.results).length,
    labelHistogram,
    bySection,
    tagHistogram,
    bucketBreakdown,
    sectionList,
    labelList,
    wptRef: wpt.ref ?? null,
    runId: wpt.runId ?? null,
    generatedAt: manifest.generatedAt ?? null,
    duration: wpt.duration ?? null,
  };
}

// ── CLI entry ───────────────────────────────────────────────────────────────

async function main() {
  const args = parseArgs(process.argv.slice(2));
  // Live runs tree (R5 moved testing/titan/ → tools/titan/; the stale path
  // made the dashboard unable to find ANY run — TITAN stale-path defect 2).
  const runsDir = path.join(REPO_ROOT, 'tools', 'titan', 'runs');
  const runId = args.run || pickLatestRunWithManifest(runsDir);
  if (!runId) {
    console.error('error: no run id supplied and no manifest.json found under', runsDir);
    process.exit(1);
  }
  const manifestPath = path.join(runsDir, runId, 'manifest.json');
  if (!existsSync(manifestPath)) {
    console.error(`error: manifest not found at ${manifestPath}`);
    process.exit(1);
  }

  const outPath = args.out
    ? path.resolve(args.out)
    // Default lands inside the live report tree next to index.html — the
    // pre-R5 `testing/report/` default wrote outside it (defect 3).
    : path.join(REPO_ROOT, 'tools', 'visual', 'report', 'titan.html');

  const bucketsPath = path.join(REPO_ROOT, 'tools', 'titan', 'wpt-buckets.json');
  const bucketsIdx = existsSync(bucketsPath)
    ? JSON.parse(await fs.readFile(bucketsPath, 'utf8')).notApplicable
    : null;
  const tagHistogramFromBuckets = existsSync(bucketsPath)
    ? JSON.parse(await fs.readFile(bucketsPath, 'utf8')).notApplicableTagHistogram
    : null;

  console.log(`reading manifest: ${path.relative(REPO_ROOT, manifestPath)}`);
  const manifestRaw = await fs.readFile(manifestPath, 'utf8');
  const manifest = JSON.parse(manifestRaw);
  console.log(`tests: ${Object.keys(manifest.wpt?.results || {}).length}`);

  const agg = aggregateManifest(manifest, bucketsIdx);
  // If the manifest's per-test tags are still empty after backfill, fall
  // back to the bucketer's pre-aggregated histogram. Safety net so the
  // tag-cloud panel always has data even when neither the manifest nor
  // the index match (e.g. `wpt-buckets.json` is missing entirely).
  if (Object.keys(agg.tagHistogram).length === 0 && tagHistogramFromBuckets) {
    agg.tagHistogram = tagHistogramFromBuckets;
  }

  const html = renderDashboard(agg, { runId, manifestPath });
  await fs.mkdir(path.dirname(outPath), { recursive: true });
  await fs.writeFile(outPath, html, 'utf8');
  console.log(`wrote ${path.relative(REPO_ROOT, outPath)} (${(html.length / 1024).toFixed(1)} KB)`);
}

function parseArgs(argv) {
  const out = { run: null, out: null };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === '--run' || a === '-r') out.run = argv[++i];
    else if (a === '--out' || a === '-o') out.out = argv[++i];
    else if (a === '--help' || a === '-h') {
      console.log('usage: render-titan.mjs [--run <runId>] [--out <path>]');
      process.exit(0);
    }
  }
  return out;
}

// Pick the most-recently-modified run directory that has a manifest.json
// inside. Skips smoke / debug / verify runs (suffix-based filter) so the
// dashboard defaults to a real swarm run unless one is explicitly named.
function pickLatestRunWithManifest(runsDir) {
  if (!existsSync(runsDir)) return null;
  const candidates = readdirSync(runsDir, { withFileTypes: true })
    .filter((d) => d.isDirectory())
    .map((d) => d.name)
    .filter((n) => existsSync(path.join(runsDir, n, 'manifest.json')))
    .map((n) => ({
      name: n,
      mtime: statSync(path.join(runsDir, n, 'manifest.json')).mtimeMs,
    }))
    .sort((a, b) => b.mtime - a.mtime);
  return candidates.length > 0 ? candidates[0].name : null;
}

// ── HTML rendering ──────────────────────────────────────────────────────────

function renderDashboard(agg, meta) {
  const totalTests = agg.totalTests;
  const labelList = agg.labelList;

  // Heatmap data: serialised once on the server side, picked up by client
  // JS for filtering (so the user can click a cell → drill into matching
  // tests without us having to re-render server-side per filter).
  const dataPayload = JSON.stringify({
    runId: meta.runId,
    wptRef: agg.wptRef,
    totalTests,
    labelHistogram: agg.labelHistogram,
    bucketBreakdown: agg.bucketBreakdown,
    tagHistogram: agg.tagHistogram,
    labelList,
    sections: agg.sectionList.map((s) => ({
      section: s.section,
      total: s.total,
      labels: s.labels,
      tests: s.tests,
    })),
  });

  const wptRefLink = agg.wptRef
    ? `<a href="https://github.com/web-platform-tests/wpt/tree/${escapeHtml(agg.wptRef)}" target="_blank" rel="noopener">${escapeHtml(agg.wptRef.slice(0, 12))}</a>`
    : '<span class="dim">unknown</span>';

  const captureMs = agg.duration?.captureMs;
  const captureHuman = captureMs ? humanDuration(captureMs) : '—';

  const generatedAt = agg.generatedAt
    ? new Date(agg.generatedAt).toLocaleString()
    : new Date().toLocaleString();

  return `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>TITAN dashboard — ${escapeHtml(meta.runId || 'unknown run')}</title>
<style>
${BASE_CSS}
</style>
</head>
<body>
<header class="top">
  <div class="title-row">
    <h1>TITAN dashboard <span class="run-id">${escapeHtml(meta.runId || '')}</span></h1>
    <p class="meta">
      <strong>${totalTests.toLocaleString()}</strong> WPT reftests ·
      ${agg.sectionList.length} spec sections ·
      WPT ref ${wptRefLink} ·
      capture ${captureHuman} ·
      generated ${escapeHtml(generatedAt)}
    </p>
  </div>

  <div class="legend-row">
    <span class="legend-title">Filters:</span>
    ${labelList.map((l) => {
      const count = agg.labelHistogram[l] || 0;
      const dim = count === 0 ? ' dim-zero' : '';
      return `<button class="chip label-chip${dim}" data-label="${escapeHtml(l)}" style="background:${titanBadgeColor(l)}" title="${escapeHtml(l)} (${count.toLocaleString()})">${escapeHtml(l)} <span class="chip-count">${count.toLocaleString()}</span></button>`;
    }).join(' ')}
    <button class="chip clear-chip" data-label="">all labels</button>
  </div>

  <div class="controls-row">
    <input type="search" id="search" placeholder="filter test paths (substring match)…" autocomplete="off">
    <span class="visible-summary" id="visibleSummary"></span>
    <span class="kbd-hint">press <kbd>/</kbd> to focus search</span>
  </div>
</header>

<main>
  <section class="panel" id="bucketPanel">
    <h2>Bucket breakdown</h2>
    <div class="bucket-grid">
      ${Object.entries(agg.bucketBreakdown)
        .sort(([a], [b]) => a.localeCompare(b))
        .map(([b, n]) => `<div class="bucket-cell"><div class="bucket-label">${escapeHtml(b)}</div><div class="bucket-count">${n.toLocaleString()}</div></div>`)
        .join('')}
    </div>
    <p class="panel-note">Bucket A = web-renderable. Bucket B = lossy-but-renderable. Bucket C = skipped (architectural exclusion). <code>missing</code> / <code>missing-extraction</code> = pipeline didn't produce comparable output.</p>
  </section>

  <section class="panel" id="heatmapPanel">
    <h2>Spec section × divergence label</h2>
    <p class="panel-note">Cell = test count. Colour intensity scales per row. Click a cell to filter the drill-down below.</p>
    <div class="heatmap-wrap">
      ${renderHeatmap(agg)}
    </div>
  </section>

  <section class="panel" id="tagPanel">
    <h2>Architectural-exclusion tags <span class="panel-sub">(${Object.values(agg.tagHistogram).reduce((a, b) => a + b, 0).toLocaleString()} matches across all tests)</span></h2>
    <p class="panel-note">Tags from <code>tools/titan/wpt-not-applicable.mjs</code>'s 17-rule scanner. A test can match multiple tags. Tests with at least one tag are auto-classified as <code>test-not-applicable</code> (FIX-E gate).</p>
    <div class="tag-cloud">
      ${Object.entries(agg.tagHistogram)
        .sort(([, a], [, b]) => b - a)
        .map(([t, n]) => {
          // Font size scales log-roughly with count. Anchor to a max so
          // the dominant tag doesn't blow out the layout.
          const max = Math.max(...Object.values(agg.tagHistogram), 1);
          const sz = 11 + Math.round(8 * Math.log10(1 + n) / Math.log10(1 + max));
          return `<span class="tag" style="font-size:${sz}px" title="${escapeHtml(t)} (${n.toLocaleString()} tests)">${escapeHtml(t)} <span class="tag-count">${n.toLocaleString()}</span></span>`;
        })
        .join(' ')}
      ${Object.keys(agg.tagHistogram).length === 0 ? '<span class="dim">no exclusion-tag data available</span>' : ''}
    </div>
  </section>

  <section class="panel" id="drilldownPanel">
    <h2>Per-section drill-down</h2>
    <p class="panel-note">Click a section to expand. Each row links to the WPT source. Sort within section: worst-severity first.</p>
    <div id="sections"></div>
  </section>
</main>

<script id="data-payload" type="application/json">${dataPayload.replace(/</g, '\\u003c')}</script>
<script>
${CLIENT_JS}
</script>
</body>
</html>`;
}

// Render the spec-section × divergence-label heatmap. Each row is one
// section, each col is one label. Cell intensity is normalised
// per-row (so a heavy `mixed` row doesn't drown out a row dominated by
// `structural-divergence`). All rendering server-side; client JS only
// adds the click → filter interaction.
function renderHeatmap(agg) {
  const cols = agg.labelList;
  const headerCells = cols
    .map((l) => `<th class="label-h" title="${escapeHtml(l)}"><span class="legend-swatch" style="background:${titanBadgeColor(l)}"></span><span class="label-h-text">${escapeHtml(l)}</span></th>`)
    .join('');

  const rows = agg.sectionList.map((sec) => {
    const max = Math.max(1, ...cols.map((l) => sec.labels[l] || 0));
    const cells = cols.map((l) => {
      const n = sec.labels[l] || 0;
      if (n === 0) return `<td class="hm-cell empty" data-section="${escapeHtml(sec.section)}" data-label="${escapeHtml(l)}" data-count="0">·</td>`;
      const intensity = Math.min(1, 0.15 + 0.85 * (n / max));
      const bg = titanBadgeColor(l);
      // CSS opacity layered over a dark cell so the colour reads as a
      // saturation gradient instead of just shading-toward-white.
      return `<td class="hm-cell" data-section="${escapeHtml(sec.section)}" data-label="${escapeHtml(l)}" data-count="${n}" style="background:${bg};opacity:${intensity.toFixed(3)}" title="${escapeHtml(sec.section)} · ${escapeHtml(l)}: ${n.toLocaleString()}">${n.toLocaleString()}</td>`;
    }).join('');
    return `<tr><th class="section-h"><a href="#sec-${escapeHtml(sec.section)}" data-section-link="${escapeHtml(sec.section)}">${escapeHtml(sec.section)}</a> <span class="section-total">${sec.total.toLocaleString()}</span></th>${cells}</tr>`;
  }).join('');

  return `<table class="heatmap">
    <thead><tr><th class="section-h">section</th>${headerCells}</tr></thead>
    <tbody>${rows}</tbody>
  </table>`;
}

function humanDuration(ms) {
  if (ms == null) return '—';
  if (ms < 60_000) return `${(ms / 1000).toFixed(1)}s`;
  if (ms < 3_600_000) return `${(ms / 60_000).toFixed(1)}m`;
  return `${(ms / 3_600_000).toFixed(1)}h`;
}

// ── CSS / JS payloads ───────────────────────────────────────────────────────

const BASE_CSS = `
* { box-sizing: border-box; }
body {
  margin: 0;
  font-family: -apple-system, system-ui, sans-serif;
  background: #0f0f17;
  color: #eee;
  font-size: 14px;
}
header.top {
  position: sticky;
  top: 0;
  z-index: 20;
  background: #161624;
  border-bottom: 1px solid #2a2a3a;
  padding: 14px 24px 10px;
}
.title-row { display: flex; align-items: baseline; gap: 16px; flex-wrap: wrap; }
.title-row h1 { margin: 0; font-size: 18px; }
.title-row h1 .run-id {
  font-family: ui-monospace, monospace;
  font-weight: 400;
  color: #888;
  font-size: 13px;
  margin-left: 8px;
}
.title-row .meta { margin: 0; color: #aaa; font-size: 12.5px; }
.title-row .meta a { color: #79c; text-decoration: none; }
.title-row .meta a:hover { text-decoration: underline; }
.title-row .meta strong { color: #fff; }
.title-row .meta .dim { color: #666; }

.legend-row {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  margin-top: 10px;
}
.legend-row .legend-title { color: #999; font-size: 11px; margin-right: 4px; }

.chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-family: ui-monospace, monospace;
  font-size: 10.5px;
  color: #fff;
  border: 1px solid rgba(255,255,255,0.18);
  border-radius: 12px;
  padding: 3px 9px;
  cursor: pointer;
  user-select: none;
  transition: filter 0.1s, transform 0.1s;
}
.chip:hover { filter: brightness(1.18); }
.chip.active { outline: 2px solid #fff; outline-offset: -2px; }
.chip.dim-zero { opacity: 0.35; }
.chip-count {
  background: rgba(0,0,0,0.35);
  border-radius: 8px;
  padding: 0 5px;
  font-size: 10px;
}
.clear-chip {
  background: #2a2a3a;
  border-color: #444;
  color: #ccc;
}

.controls-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 10px;
  flex-wrap: wrap;
}
.controls-row input[type="search"] {
  flex: 1;
  min-width: 220px;
  max-width: 480px;
  padding: 6px 10px;
  background: rgba(255,255,255,0.06);
  border: 1px solid rgba(255,255,255,0.18);
  border-radius: 4px;
  color: #fff;
  font-size: 13px;
  outline: none;
}
.controls-row input[type="search"]:focus { border-color: #79c; }
.visible-summary { color: #999; font-size: 12px; }
.kbd-hint { color: #666; font-size: 11px; margin-left: auto; }
kbd {
  font-family: ui-monospace, monospace;
  background: rgba(255,255,255,0.1);
  padding: 1px 4px;
  border-radius: 3px;
  font-size: 10px;
}

main { padding: 16px 24px; max-width: 1600px; margin: 0 auto; }
.panel {
  background: #16161f;
  border: 1px solid #2a2a3a;
  border-radius: 8px;
  padding: 14px 18px;
  margin-bottom: 18px;
}
.panel h2 {
  margin: 0 0 6px 0;
  font-size: 15px;
  font-weight: 600;
  display: flex;
  align-items: baseline;
  gap: 8px;
}
.panel h2 .panel-sub {
  font-size: 11.5px;
  color: #888;
  font-weight: 400;
}
.panel-note { margin: 0 0 10px 0; color: #888; font-size: 12px; }
.panel-note code {
  font-family: ui-monospace, monospace;
  background: rgba(255,255,255,0.06);
  padding: 1px 4px;
  border-radius: 3px;
  font-size: 11px;
}

.bucket-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
  gap: 10px;
  margin-bottom: 6px;
}
.bucket-cell {
  background: #0e0e18;
  border: 1px solid #232333;
  border-radius: 5px;
  padding: 10px;
  text-align: center;
}
.bucket-label { font-family: ui-monospace, monospace; color: #aaa; font-size: 11px; margin-bottom: 4px; }
.bucket-count { font-size: 18px; font-weight: 600; color: #fff; }

/* ── Heatmap ─────────────────────────────────────────────────────────── */
.heatmap-wrap { overflow-x: auto; }
table.heatmap {
  width: 100%;
  border-collapse: collapse;
  font-size: 11px;
}
table.heatmap th, table.heatmap td {
  border: 1px solid #232333;
  padding: 4px 6px;
  text-align: center;
  white-space: nowrap;
}
table.heatmap thead th {
  background: #1a1a2a;
  color: #ccc;
  font-weight: 500;
  font-size: 10.5px;
}
table.heatmap th.label-h { writing-mode: vertical-rl; transform: rotate(180deg); height: 110px; vertical-align: bottom; }
table.heatmap th.label-h .legend-swatch {
  display: inline-block;
  width: 8px; height: 8px;
  margin-right: 4px;
  border-radius: 2px;
  vertical-align: middle;
}
table.heatmap th.label-h .label-h-text { font-family: ui-monospace, monospace; }
table.heatmap th.section-h {
  text-align: left;
  background: #16162a;
  font-family: ui-monospace, monospace;
  font-size: 11px;
  color: #cce;
  padding: 4px 8px;
  position: sticky;
  left: 0;
  z-index: 1;
}
table.heatmap th.section-h a { color: #cce; text-decoration: none; }
table.heatmap th.section-h a:hover { color: #fff; text-decoration: underline; }
table.heatmap th.section-h .section-total { color: #888; margin-left: 4px; font-size: 10px; }
table.heatmap td.hm-cell {
  font-family: ui-monospace, monospace;
  cursor: pointer;
  color: #fff;
  font-weight: 500;
  min-width: 36px;
}
table.heatmap td.hm-cell.empty {
  background: #0c0c14;
  color: #333;
  cursor: default;
  font-weight: 300;
}
table.heatmap td.hm-cell:hover:not(.empty) {
  outline: 2px solid #fff;
  outline-offset: -2px;
}

/* ── Tag cloud ───────────────────────────────────────────────────────── */
.tag-cloud {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 10px;
  align-items: baseline;
}
.tag {
  font-family: ui-monospace, monospace;
  color: #cce;
  background: rgba(120, 140, 200, 0.08);
  border: 1px solid rgba(120, 140, 200, 0.2);
  border-radius: 4px;
  padding: 2px 8px;
  cursor: default;
  white-space: nowrap;
}
.tag .tag-count { color: #889; font-size: 0.85em; margin-left: 4px; }

/* ── Drill-down per-section ──────────────────────────────────────────── */
.section-block {
  border: 1px solid #232333;
  background: #11111c;
  border-radius: 6px;
  margin-bottom: 6px;
}
.section-summary {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  cursor: pointer;
  user-select: none;
  font-family: ui-monospace, monospace;
  font-size: 12.5px;
}
.section-summary:hover { background: #161626; }
.section-summary .caret { color: #888; width: 10px; display: inline-block; }
.section-summary .section-title { font-weight: 600; }
.section-summary .section-total-pill {
  background: rgba(255,255,255,0.08);
  color: #ccc;
  padding: 1px 7px;
  border-radius: 8px;
  font-size: 10.5px;
}
.section-summary .mini-bar {
  display: inline-flex;
  height: 6px;
  border-radius: 3px;
  overflow: hidden;
  background: #0a0a14;
  flex: 1;
  max-width: 360px;
  margin-left: auto;
}
.section-summary .mini-bar > span { display: inline-block; height: 100%; }

.section-tests {
  display: none;
  border-top: 1px solid #232333;
  background: #0c0c16;
  padding: 4px 0;
}
.section-block.open .section-tests { display: block; }
.section-block.open .section-summary .caret::before { content: '▾'; }
.section-block .section-summary .caret::before { content: '▸'; }

.test-row {
  display: grid;
  /* badge · path · label · 5 metrics (web/iOS/And SSIM, ΔE, Δpx) — the
     three per-platform browser-ref columns are the Phase-4 addition. */
  grid-template-columns: 24px 1fr 120px 58px 58px 58px 58px 58px;
  gap: 8px;
  align-items: center;
  padding: 4px 12px;
  font-family: ui-monospace, monospace;
  font-size: 11.5px;
  border-bottom: 1px solid #15151f;
}
.test-row:last-child { border-bottom: none; }
.test-row:hover { background: #14142a; }
.test-row .label-badge {
  display: inline-block;
  font-size: 9.5px;
  color: #fff;
  border-radius: 3px;
  padding: 1px 5px;
  text-align: center;
  width: 24px;
}
.test-row .test-path { color: #cce; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.test-row .test-path a { color: #cce; text-decoration: none; }
.test-row .test-path a:hover { color: #fff; text-decoration: underline; }
.test-row .label-name {
  font-family: ui-monospace, monospace;
  font-size: 10.5px;
  color: #fff;
  border-radius: 3px;
  padding: 1px 6px;
  text-align: center;
  display: inline-block;
}
.test-row .metric { color: #999; text-align: right; font-size: 10.5px; }
.test-row .metric b { color: #fff; font-weight: 500; }
.test-row .na-tags { color: #889; font-size: 10px; }

.dim { color: #666; }
`;

const CLIENT_JS = `
(() => {
  const data = JSON.parse(document.getElementById('data-payload').textContent);
  const sectionsRoot = document.getElementById('sections');
  const search = document.getElementById('search');
  const visibleSummary = document.getElementById('visibleSummary');

  const state = {
    activeLabel: null,    // null = all
    activeSection: null,  // null = all sections
    query: '',
  };

  // ── Render section blocks (server-side data, client-side filter) ──────
  // We render all sections + all tests once, then toggle visibility on
  // filter changes. ~10k DOM rows is manageable; keeps interactions
  // snappy without paginating.
  function renderSections() {
    const frag = document.createDocumentFragment();
    for (const sec of data.sections) {
      const block = document.createElement('div');
      block.className = 'section-block';
      block.id = 'sec-' + sec.section;
      block.dataset.section = sec.section;

      // Stacked-bar mini visualisation: each label segment proportional
      // to its share of the section. Helps the user scan which sections
      // are dominated by which labels without expanding the section.
      const miniBar = data.labelList.map((l) => {
        const n = sec.labels[l] || 0;
        if (n === 0) return '';
        const pct = (n / sec.total) * 100;
        return '<span style="width:' + pct.toFixed(2) + '%;background:' + labelColor(l) + '" title="' + l + ' ' + n + '"></span>';
      }).join('');

      block.innerHTML =
        '<div class="section-summary" data-toggle>' +
          '<span class="caret"></span>' +
          '<span class="section-title">' + escapeHtml(sec.section) + '</span>' +
          '<span class="section-total-pill">' + sec.total.toLocaleString() + ' tests</span>' +
          '<span class="mini-bar">' + miniBar + '</span>' +
        '</div>' +
        '<div class="section-tests"></div>';

      const testsContainer = block.querySelector('.section-tests');
      // Lazy-render rows on first expand: ~30 sections × ~300 tests
      // each is ~9k initial DOM nodes if we eagerly render. Lazy keeps
      // first-paint snappy. The dataset is stashed on the container so
      // filter logic can show/hide before the rows actually mount.
      testsContainer.dataset.tests = JSON.stringify(sec.tests);
      testsContainer.dataset.rendered = '0';
      frag.appendChild(block);
    }
    sectionsRoot.appendChild(frag);

    // Click handler: toggle expanded + lazy-render rows on first open.
    sectionsRoot.addEventListener('click', (e) => {
      const summary = e.target.closest('[data-toggle]');
      if (!summary) return;
      const block = summary.closest('.section-block');
      if (!block) return;
      block.classList.toggle('open');
      if (block.classList.contains('open')) ensureSectionRendered(block);
    });
  }

  function ensureSectionRendered(block) {
    const c = block.querySelector('.section-tests');
    if (!c || c.dataset.rendered === '1') return;
    const tests = JSON.parse(c.dataset.tests || '[]');
    const html = tests.map((t) => renderTestRow(t)).join('');
    c.innerHTML = html;
    c.dataset.rendered = '1';
    // Re-apply current filter to the newly-rendered rows.
    applyFilter();
  }

  function renderTestRow(t) {
    const link = buildWptUrl(t.path);
    const ssimStr = t.ssim != null ? Number(t.ssim).toFixed(3) : '—';
    const labP95Str = t.labP95 != null ? Number(t.labP95).toFixed(1) : '—';
    const pixStr = t.pixelPct != null ? Number(t.pixelPct).toFixed(1) + '%' : '—';
    // Phase-4 native columns. '—' = platform not captured this run — an
    // EXPLICIT gap the reader can see and go fill, never a silent one.
    const iosStr = t.iosSsim != null ? Number(t.iosSsim).toFixed(3) : '—';
    const droidStr = t.androidSsim != null ? Number(t.androidSsim).toFixed(3) : '—';
    const tagsStr = (t.naTags && t.naTags.length)
      ? '<span class="na-tags" title="auto-bucketer tags">' + t.naTags.map(escapeHtml).join(', ') + '</span>'
      : '';
    return '<div class="test-row" data-label="' + escapeHtml(t.label) + '" data-path="' + escapeHtml(t.path) + '">' +
      '<span class="label-badge" style="background:' + labelColor(t.label) + '" title="' + escapeHtml(t.label) + '">' + labelBadgeAbbrev(t.label) + '</span>' +
      '<span class="test-path"><a href="' + escapeHtml(link) + '" target="_blank" rel="noopener">' + escapeHtml(t.path) + '</a> ' + tagsStr + '</span>' +
      '<span class="label-name" style="background:' + labelColor(t.label) + '">' + escapeHtml(t.label) + '</span>' +
      '<span class="metric" title="web SSIM vs browser-ref">web <b>' + ssimStr + '</b></span>' +
      '<span class="metric" title="iOS SSIM vs browser-ref">iOS <b>' + iosStr + '</b></span>' +
      '<span class="metric" title="Android SSIM vs browser-ref">And <b>' + droidStr + '</b></span>' +
      '<span class="metric" title="CIEDE2000 ΔE p95">ΔE <b>' + labP95Str + '</b></span>' +
      '<span class="metric" title="pixel mismatch percent">Δpx <b>' + pixStr + '</b></span>' +
    '</div>';
  }

  // Two-letter abbreviation for the badge column (saves horizontal space
  // in the dense per-test grid). The full label is also rendered as a
  // pill in its own column, so this is purely for at-a-glance scan.
  function labelBadgeAbbrev(label) {
    const abbr = {
      'identical': 'ID',
      'sub-pixel-noise': 'SP',
      'color-drift': 'CD',
      'edge-shift': 'ES',
      'mixed': 'MX',
      'structural-divergence': 'SD',
      'no-content': 'NC',
      'test-not-applicable': 'NA',
      'no-data': 'ND',
      'unknown': '??',
    };
    return abbr[label] || '?';
  }

  // Mirror of tools/visual/classify-divergence.mjs:badgeColor + titanBadgeColor —
  // duplicated client-side because the renderer is fully self-contained
  // (no module fetch / CDN). If the server-side palette changes, update
  // both sites; the unit tests pin both.
  function labelColor(label) {
    switch (label) {
      case 'identical':         return '#2d7a3a';
      case 'sub-pixel-noise':   return '#4a9b5e';
      case 'color-drift':       return '#a8830a';
      case 'edge-shift':        return '#a8830a';
      case 'structural-divergence': return '#a33';
      case 'mixed':             return '#c46a1f';
      case 'unknown':           return '#666';
      case 'no-content':        return '#9b3a8e';
      case 'test-not-applicable': return '#2c3340';
      case 'no-data':           return '#3a3530';
      default:                  return '#666';
    }
  }

  // Build a github.com URL for the WPT test path. Mirror of the
  // server-side buildWptSourceUrl helper; pinned to the same WPT ref
  // the dashboard was built against.
  function buildWptUrl(testPath) {
    const ref = (data.wptRef && /^[0-9a-f]{7,40}$/i.test(data.wptRef)) ? data.wptRef : 'master';
    const enc = testPath.split('/').map(encodeURIComponent).join('/');
    return 'https://github.com/web-platform-tests/wpt/blob/' + ref + '/' + enc;
  }

  function escapeHtml(s) {
    return String(s == null ? '' : s).replace(/[&<>"']/g, (c) => ({
      '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
    }[c]));
  }

  // ── Filtering ─────────────────────────────────────────────────────────
  function applyFilter() {
    const q = state.query;
    const lbl = state.activeLabel;
    const sect = state.activeSection;

    let visibleTests = 0;
    let visibleSections = 0;

    for (const block of sectionsRoot.querySelectorAll('.section-block')) {
      const secName = block.dataset.section;
      const sectionMatches = !sect || sect === secName;
      let sectionShouldShow = sectionMatches;

      // If the user clicked a label chip but not yet a section, we still
      // want to hide sections whose label-count for that label is zero —
      // less noise.
      if (sectionShouldShow && lbl && data.sections.find((s) => s.section === secName)?.labels?.[lbl] === 0 && !q) {
        sectionShouldShow = false;
      }

      block.style.display = sectionShouldShow ? '' : 'none';
      if (!sectionShouldShow) continue;

      // Tests inside this section. Only walk rows if rendered (lazy).
      let sectionVisibleHere = 0;
      const c = block.querySelector('.section-tests');
      if (c.dataset.rendered === '1') {
        for (const row of c.querySelectorAll('.test-row')) {
          const matchesLabel = !lbl || row.dataset.label === lbl;
          const matchesQuery = !q || row.dataset.path.toLowerCase().includes(q);
          const show = matchesLabel && matchesQuery;
          row.style.display = show ? '' : 'none';
          if (show) sectionVisibleHere++;
        }
      } else {
        // For collapsed sections we approximate via the histogram so the
        // visible-summary count is sensible even before expanding. Note:
        // a query string while the section is collapsed cannot be matched
        // path-wise (we don't have rows to scan), so we conservatively
        // count zero in that case — the search box's auto-expand path
        // ensures the user actually sees the matches.
        const sec = data.sections.find((s) => s.section === secName);
        if (sec) {
          if (q) {
            sectionVisibleHere = 0;
          } else if (lbl) {
            sectionVisibleHere = sec.labels[lbl] || 0;
          } else {
            sectionVisibleHere = sec.total;
          }
        }
      }

      visibleTests += sectionVisibleHere;
      // Only count the section toward the section tally if at least one
      // test inside it is visible — otherwise the user sees "10 tests
      // across 30 sections" with most sections rendering as empty
      // headers, which is misleading.
      if (sectionVisibleHere > 0) {
        visibleSections++;
        block.style.display = '';
      } else {
        block.style.display = 'none';
      }
    }

    visibleSummary.textContent =
      visibleTests.toLocaleString() + ' tests across ' + visibleSections + ' section(s)';
  }

  // ── Wire up controls ──────────────────────────────────────────────────
  function setActiveLabel(label) {
    state.activeLabel = label || null;
    for (const c of document.querySelectorAll('.label-chip')) {
      c.classList.toggle('active', c.dataset.label === label);
    }
    applyFilter();
  }
  function setActiveSection(section) {
    state.activeSection = section || null;
    for (const t of document.querySelectorAll('.hm-cell')) {
      t.classList.toggle('active', false);
    }
    if (section) {
      const block = document.getElementById('sec-' + section);
      if (block) {
        block.classList.add('open');
        ensureSectionRendered(block);
      }
    }
    applyFilter();
  }

  function init() {
    renderSections();

    // Label chips (top filter row).
    document.querySelectorAll('.label-chip').forEach((c) => {
      c.addEventListener('click', () => {
        const wasActive = c.classList.contains('active');
        setActiveLabel(wasActive ? null : c.dataset.label);
      });
    });
    document.querySelector('.clear-chip').addEventListener('click', () => {
      setActiveLabel(null);
      setActiveSection(null);
      search.value = '';
      state.query = '';
      applyFilter();
    });

    // Heatmap cells: click a cell → filter to that label + section.
    document.querySelectorAll('.hm-cell:not(.empty)').forEach((cell) => {
      cell.addEventListener('click', () => {
        const label = cell.dataset.label;
        const section = cell.dataset.section;
        setActiveLabel(label);
        setActiveSection(section);
        // Scroll drill-down into view.
        document.getElementById('drilldownPanel').scrollIntoView({ behavior: 'smooth' });
      });
    });

    // Section header link (in the heatmap row label) — same behaviour
    // but without the label filter.
    document.querySelectorAll('a[data-section-link]').forEach((a) => {
      a.addEventListener('click', (e) => {
        e.preventDefault();
        setActiveSection(a.dataset.sectionLink);
        document.getElementById('drilldownPanel').scrollIntoView({ behavior: 'smooth' });
      });
    });

    // Search box.
    search.addEventListener('input', () => {
      state.query = search.value.toLowerCase().trim();
      // Force render of all open sections so search hits within them
      // are visible. Search across collapsed sections is approximate
      // (count-only) — the row-level match needs the rows mounted.
      if (state.query) {
        for (const block of sectionsRoot.querySelectorAll('.section-block')) {
          ensureSectionRendered(block);
          block.classList.add('open');
        }
      }
      applyFilter();
    });

    // Keyboard: '/' focuses search.
    document.addEventListener('keydown', (e) => {
      if (e.key === '/' && document.activeElement !== search) {
        e.preventDefault();
        search.focus();
        search.select();
      }
    });

    applyFilter();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
`;

// ── Run as CLI ──────────────────────────────────────────────────────────────

// Only execute main() when invoked as a script (not when imported by tests).
if (import.meta.url === `file://${process.argv[1]}`) {
  main().catch((e) => {
    console.error('render-titan: fatal:', e);
    process.exit(1);
  });
}
