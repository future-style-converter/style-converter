#!/usr/bin/env node
//
// baseline-stats.mjs
//
// COMPARE_METRICS.md Section 7 item 8 — empirical classifier baseline pass.
//
// Walks every pair in tools/visual/report/manifest.json, runs each through
// `classifyDivergence`, and emits:
//
//   1. To stdout: one-line label distribution table for quick eyeballing.
//   2. To tools/visual/baseline-stats.json: structured per-pair classifications
//      so downstream tooling (the Phase B regression-check in
//      compare-screenshots.mjs item 10) can detect distribution drift on
//      future PRs.
//
// This is the empirical baseline: if a future PR shifts the distribution
// (more `structural-divergence`, fewer `identical`), Phase B logs a
// warning. Phase C (future) gates CI on it.
//
// Usage:
//     node tools/visual/baseline-stats.mjs
//     node tools/visual/baseline-stats.mjs --manifest path/to/manifest.json
//
// Exit codes:
//     0 — wrote stats successfully
//     2 — manifest missing or unreadable

import { readFileSync, writeFileSync, existsSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

import {
  classifyDivergence,
  CLASSIFIER_VERSION,
} from './classify-divergence.mjs';

const __dirname = dirname(fileURLToPath(import.meta.url));

// ── Args ────────────────────────────────────────────────────────────────────
const args = process.argv.slice(2);
function getArg(name) {
  const i = args.indexOf(name);
  return i >= 0 ? args[i + 1] : undefined;
}

const manifestPath =
  getArg('--manifest') ?? resolve(__dirname, 'report/manifest.json');
const outPath =
  getArg('--out') ?? resolve(__dirname, 'baseline-stats.json');

// ── Load manifest ───────────────────────────────────────────────────────────
if (!existsSync(manifestPath)) {
  console.error(`✗ manifest not found: ${manifestPath}`);
  console.error('  run `node tools/visual/compare-screenshots.mjs` first');
  process.exit(2);
}

let manifest;
try {
  manifest = JSON.parse(readFileSync(manifestPath, 'utf-8'));
} catch (e) {
  console.error(`✗ failed to parse manifest: ${e.message ?? e}`);
  process.exit(2);
}

// ── Walk every pair, classify, accumulate ───────────────────────────────────
//
// The manifest shape (Section 6): rows[].pairs[pairKey] where pairKey is
// one of "iOS-Android" / "iOS-web" / "Android-web". Each pair is either
// null (one platform missing/decode-failed) or an ImagePairMetrics block.
// We classify each non-null pair once and key the perPair map by the
// "<row.name>__<pairKey>" string the regression-check Phase B will use.

// Empty buckets up-front so the JSON output always lists every label
// (even when count = 0) — keeps downstream diffing stable. `no-content`
// (round 91 / classifier v2) is appended LAST so the canonical 7-label
// order from v1 manifests stays stable for diffing tools.
const perLabel = {
  identical: 0,
  'sub-pixel-noise': 0,
  'color-drift': 0,
  'edge-shift': 0,
  'structural-divergence': 0,
  mixed: 0,
  unknown: 0,
  'no-content': 0,
};
const perPair = {};
let totalPairs = 0;

for (const row of manifest.rows ?? []) {
  for (const [pairKey, pair] of Object.entries(row.pairs ?? {})) {
    // Null pairs are not counted: there's no metrics block to classify.
    // (A row with all-null pairs typically means decode failure on one
    // side — Phase B would flag those separately, not here.)
    if (!pair) continue;
    const label = classifyDivergence(pair);
    perLabel[label] = (perLabel[label] ?? 0) + 1;
    perPair[`${row.name}__${pairKey}`] = label;
    totalPairs += 1;
  }
}

// ── Stdout: one-line distribution table ─────────────────────────────────────
//
// Format: `identical: 287 · sub-pixel-noise: 32 · color-drift: 5 · ...`
// Spec example uses ` · ` separators. We honour every label's order from
// the perLabel object so the line reads in severity order from the
// classifier's perspective (identical first).
const distribution = Object.entries(perLabel)
  .map(([label, count]) => `${label}: ${count}`)
  .join(' · ');

console.log(`→ classified ${totalPairs} pair(s) from ${manifestPath}`);
console.log(`  ${distribution}`);

// ── JSON output ─────────────────────────────────────────────────────────────
const out = {
  generated: new Date().toISOString(),
  classifierVersion: CLASSIFIER_VERSION,
  manifestVersion: manifest.manifestVersion ?? 1,
  inputLabel: manifest.inputLabel ?? '',
  totalPairs,
  perLabel,
  perPair,
};

writeFileSync(outPath, JSON.stringify(out, null, 2));
console.log(`✓ wrote ${outPath}`);
