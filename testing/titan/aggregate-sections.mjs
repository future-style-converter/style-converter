#!/usr/bin/env node
//
// testing/titan/aggregate-sections.mjs
//
// Phase-2 reducer that merges the per-section v4 manifests written by
// testing/titan/section-runner.sh into a unified TITAN-run manifest.
//
// Why a separate aggregator (rather than baking into the section runner)?
//   1. Section runners can finish out-of-order (some sections take 30s,
//      some take 30 min). The aggregator runs ONCE at the end, after
//      every dispatched agent has settled.
//   2. SWARM-2B (the 30-agent dispatcher) needs to be able to call this
//      from arbitrary languages — Node ESM is the lingua franca for the
//      rest of the testing/ tooling, so we keep it in-tree as a module.
//   3. Pure ESM with `export function aggregate(runDir)` lets us unit-
//      test the merge logic without a live filesystem (we feed in a
//      synthetic dir).
//
// Input layout (produced by section-runner.sh):
//   testing/titan/runs/<runId>/sections/<section>/manifest.json
//
// Output:
//   testing/titan/runs/<runId>/manifest.json    (unified v4)
//   stdout: one-line summary + per-section table.
//
// Aggregation rules:
//   - manifestVersion: 4 (preserved)
//   - thresholds: taken from the FIRST section's manifest (they're stable
//     across compare-screenshots invocations, so any section's copy is
//     authoritative).
//   - rows: union — sections never share component keys (the per-section
//     wpt__<section>__<stem>__<idx> namespacing guarantees this).
//   - wpt.totalTests: sum across sections.
//   - wpt.buckets: element-wise sum of per-section A/B/C counts.
//   - wpt.results: union (no key overlap — keyed by repo-relative WPT
//     test path, sections own disjoint paths).
//   - wpt.skipped: union (same disjoint guarantee).
//   - wpt.runId: set to the parent runId (the dir we're aggregating).
//   - wpt.duration.captureMs: max across sections (wall-clock of the
//     slowest section bounds the total parallel run).
//
// Usage:
//   node aggregate-sections.mjs <runDir>
//   node aggregate-sections.mjs testing/titan/runs/20260511T...

import { promises as fs } from 'node:fs';
import { existsSync } from 'node:fs';
import { resolve, join, basename, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = resolve(__dirname, '..', '..');

// ── Pure aggregation logic (exported for tests) ──────────────────────────────

/**
 * Merge an array of per-section v4 manifests into one. Pure function —
 * no IO. Tests feed in synthetic objects so the merge logic stays
 * exercisable without spinning up section-runner.sh.
 *
 * @param {Array<{section: string, manifest: object}>} entries
 * @param {{ runId?: string }} [opts]
 * @returns {object} unified v4 manifest
 */
export function mergeManifests(entries, opts = {}) {
  // Empty input still produces a syntactically-valid v4 manifest so
  // downstream consumers (titan dashboard, smoke-stats) don't have to
  // special-case the "no sections completed" state.
  if (!entries || entries.length === 0) {
    return {
      manifestVersion: 4,
      generatedAt: new Date().toISOString(),
      inputLabel: 'titan-aggregated (empty)',
      thresholds: null,
      perPlatformProbes: null,
      wpt: {
        ref: null,
        runId: opts.runId ?? null,
        totalTests: 0,
        buckets: { A: 0, B: 0, C: 0 },
        duration: { captureMs: null, compareMs: null },
        results: {},
        skipped: {},
        sections: {},
      },
      rows: [],
    };
  }

  // Take thresholds + ref from the first non-null section. They're stable
  // across runs of compare-screenshots (the v4 spec pins the thresholds
  // block), so taking any one is correct.
  const first = entries[0].manifest;
  const thresholds = first.thresholds ?? null;
  const ref = first.wpt?.ref ?? null;

  // Aggregate state.
  const rows = [];
  const results = {};
  const skipped = {};
  const sections = {};
  let totalTests = 0;
  let bucketA = 0, bucketB = 0, bucketC = 0;
  let maxCaptureMs = null;
  let maxCompareMs = null;

  for (const { section, manifest } of entries) {
    if (!manifest) continue;
    const w = manifest.wpt ?? {};
    // Per-section stats stored under wpt.sections so the dashboard can
    // colour rows by source section.
    sections[section] = {
      totalTests: w.totalTests ?? 0,
      buckets: w.buckets ?? { A: 0, B: 0, C: 0 },
      duration: w.duration ?? null,
      runId: w.runId ?? null,
    };

    totalTests += w.totalTests ?? 0;
    bucketA   += w.buckets?.A ?? 0;
    bucketB   += w.buckets?.B ?? 0;
    bucketC   += w.buckets?.C ?? 0;

    // Wall-clock: the slowest section bounds the total parallel run
    // duration. Sequential sum would overstate by Nx.
    if (w.duration?.captureMs != null) {
      maxCaptureMs = Math.max(maxCaptureMs ?? 0, w.duration.captureMs);
    }
    if (w.duration?.compareMs != null) {
      maxCompareMs = Math.max(maxCompareMs ?? 0, w.duration.compareMs);
    }

    // results / skipped: union. Component-key namespacing
    // (wpt__<section>__<stem>__<idx>) plus repo-relative path keys
    // ensure two sections never collide. We assert that explicitly so a
    // future bucket overlap would fail loudly rather than silently
    // overwrite.
    for (const [k, v] of Object.entries(w.results ?? {})) {
      if (k in results) {
        throw new Error(`aggregate-sections: duplicate result key '${k}' from section '${section}'`);
      }
      results[k] = v;
    }
    for (const [k, v] of Object.entries(w.skipped ?? {})) {
      // skipped tests CAN overlap (a test in bucket-C is the same C entry
      // for every section that asked for it). Keep first-write-wins; the
      // reason text is the same anyway.
      if (!(k in skipped)) skipped[k] = v;
    }

    // Per-pair rows: just append. Component names are namespaced per
    // section in build-combined-fixture.componentKey() so duplicates are
    // structurally impossible.
    for (const r of manifest.rows ?? []) rows.push(r);
  }

  return {
    manifestVersion: 4,
    generatedAt: new Date().toISOString(),
    inputLabel: `titan-aggregated (${entries.length} sections)`,
    thresholds,
    perPlatformProbes: null,
    wpt: {
      ref,
      runId: opts.runId ?? null,
      totalTests,
      buckets: { A: bucketA, B: bucketB, C: bucketC },
      duration: { captureMs: maxCaptureMs, compareMs: maxCompareMs },
      results,
      skipped,
      sections,
    },
    rows,
  };
}

/**
 * Derive a one-line + multi-line summary from an aggregated manifest.
 * Returns { line, table } so callers can pipe / format independently.
 */
export function summarize(unified) {
  const w = unified.wpt ?? {};
  const labels = {};
  for (const r of Object.values(w.results ?? {})) {
    const l = r.divergence ?? 'unknown';
    labels[l] = (labels[l] ?? 0) + 1;
  }
  const order = ['identical','sub-pixel-noise','color-drift','edge-shift',
                 'structural-divergence','mixed','unknown','no-data'];
  const seen = Object.keys(labels).filter(k => !order.includes(k));
  const distribution = [...order, ...seen]
    .filter(k => labels[k])
    .map(k => `${k}: ${labels[k]}`)
    .join(' · ');

  const sections = w.sections ?? {};
  const sectionRows = Object.entries(sections)
    .sort(([a],[b]) => a.localeCompare(b))
    .map(([s, info]) => {
      const total = info.totalTests ?? 0;
      const dur = info.duration?.captureMs;
      const durStr = dur != null ? `${(dur/1000).toFixed(1)}s` : '—';
      return `  ${s.padEnd(28)} ${String(total).padStart(5)} tests · ${durStr}`;
    });

  const line = `aggregated ${Object.keys(sections).length} sections · ` +
               `${w.totalTests ?? 0} tests · ` +
               `A=${w.buckets?.A ?? 0} B=${w.buckets?.B ?? 0} C=${w.buckets?.C ?? 0} · ` +
               `${distribution || 'no classifier data'}`;
  return { line, table: sectionRows.join('\n') };
}

// ── IO wrapper ──────────────────────────────────────────────────────────────

/**
 * Walk runDir/sections/, read every manifest.json, and emit a unified
 * manifest at runDir/manifest.json. Returns the unified manifest.
 *
 * @param {string} runDir absolute or repo-relative path to the run dir
 * @returns {Promise<object>} unified manifest (also written to disk)
 */
export async function aggregate(runDir) {
  const abs = resolve(runDir);
  const sectionsDir = join(abs, 'sections');
  if (!existsSync(sectionsDir)) {
    throw new Error(`aggregate-sections: ${sectionsDir} does not exist (no sections to merge)`);
  }
  const subdirs = (await fs.readdir(sectionsDir, { withFileTypes: true }))
    .filter(d => d.isDirectory())
    .map(d => d.name)
    .sort();

  const entries = [];
  for (const section of subdirs) {
    const manifestPath = join(sectionsDir, section, 'manifest.json');
    if (!existsSync(manifestPath)) {
      // Section started but didn't finish (extract or capture failure).
      // Surface the gap in stderr so the dispatcher logs reflect reality;
      // don't silently skip — the resulting manifest's wpt.sections would
      // misrepresent coverage.
      process.stderr.write(`  ⚠ ${section}: no manifest.json (section incomplete)\n`);
      continue;
    }
    const raw = await fs.readFile(manifestPath, 'utf8');
    const parsed = JSON.parse(raw);
    // Detect skeleton manifests written by section-runner.sh's Step 6.5
    // beacon that never got upgraded by inject-wpt-block.mjs (Step 7 was
    // killed mid-run). These manifests have `wpt: { incomplete: true }` and
    // empty results — including them would silently zero-fill the section
    // in the aggregate. Skip with a loud warning so the dispatcher logs
    // surface "this section needs re-running."
    if (parsed.wpt?.incomplete === true) {
      process.stderr.write(
        `  ⚠ ${section}: manifest.wpt has incomplete:true marker — re-run section-runner.sh ${section}\n`
      );
      continue;
    }
    // Belt-and-suspenders: a manifest with no wpt block at all is the
    // pre-fix race-bug symptom — surface and skip rather than zero-fill.
    if (!parsed.wpt || typeof parsed.wpt.totalTests !== 'number') {
      process.stderr.write(
        `  ⚠ ${section}: manifest has no wpt block (race bug / unpatched runner) — re-run section-runner.sh ${section}\n`
      );
      continue;
    }
    entries.push({ section, manifest: parsed });
  }

  // Use the basename of the run dir as the runId. section-runner names
  // run dirs <iso>-<section> for parallel safety; this aggregator runs at
  // the parent dir so the runId is the parent's basename.
  const runId = basename(abs);
  const unified = mergeManifests(entries, { runId });

  const out = join(abs, 'manifest.json');
  await fs.writeFile(out, JSON.stringify(unified, null, 2) + '\n', 'utf8');
  return unified;
}

// ── CLI ─────────────────────────────────────────────────────────────────────

async function main() {
  const arg = process.argv[2];
  if (!arg) {
    console.error('usage: aggregate-sections.mjs <runDir>');
    console.error('       <runDir> contains sections/<name>/manifest.json files');
    process.exit(1);
  }
  const unified = await aggregate(arg);
  const { line, table } = summarize(unified);
  console.log(line);
  if (table) console.log(table);
  console.log(`\n→ wrote ${join(resolve(arg), 'manifest.json')}`);
}

if (import.meta.url === `file://${process.argv[1]}`) {
  main().catch((err) => {
    console.error('aggregate-sections: fatal:', err);
    process.exit(2);
  });
}
