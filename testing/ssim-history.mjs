#!/usr/bin/env node
// SSIM regression-history aggregator (Tier 8).
//
// Reads any *_results.txt files in /tmp/tier*_results.txt and any current
// test-all output, then appends a snapshot to testing/ssim-history.json.
// CI can diff the latest two snapshots and fail if any fixture drops > 0.05.

import { readFileSync, writeFileSync, existsSync, readdirSync } from 'node:fs';
import { execSync } from 'node:child_process';
import { join } from 'node:path';

const HISTORY_PATH = 'testing/ssim-history.json';
const REGRESSION_THRESHOLD = 0.05;

// Load history (or seed empty)
const history = existsSync(HISTORY_PATH)
  ? JSON.parse(readFileSync(HISTORY_PATH, 'utf8'))
  : { snapshots: [] };

// Build today's snapshot from /tmp/tier*_results.txt
const snapshot = {
  date: new Date().toISOString(),
  commit: execSync('git rev-parse HEAD').toString().trim(),
  fixtures: {},
};

const tmpFiles = readdirSync('/tmp').filter(f => /^tier\d+.*_results\.txt$/.test(f));
for (const file of tmpFiles) {
  const text = readFileSync(join('/tmp', file), 'utf8');
  for (const line of text.split('\n')) {
    const m = line.match(/^(PASS|FAIL)\s+min=([\d.]+)\s+(?:rows=\d+\s+)?(.+)$/);
    if (!m) continue;
    const [, status, ssim, fixture] = m;
    snapshot.fixtures[fixture.trim()] = { status, ssim: parseFloat(ssim) };
  }
}

// Diff against previous snapshot
const previous = history.snapshots[history.snapshots.length - 1];
const regressions = [];
if (previous) {
  for (const [fixture, { ssim }] of Object.entries(snapshot.fixtures)) {
    const prev = previous.fixtures[fixture]?.ssim;
    if (prev !== undefined && prev - ssim > REGRESSION_THRESHOLD) {
      regressions.push({ fixture, before: prev, after: ssim, drop: prev - ssim });
    }
  }
}

history.snapshots.push(snapshot);
// Cap at 30 snapshots to avoid unbounded growth
if (history.snapshots.length > 30) history.snapshots.shift();

writeFileSync(HISTORY_PATH, JSON.stringify(history, null, 2));

console.log(`Snapshot: ${Object.keys(snapshot.fixtures).length} fixtures`);
console.log(`Total snapshots in history: ${history.snapshots.length}`);
if (regressions.length > 0) {
  console.error(`\n❌ ${regressions.length} REGRESSIONS (drop > ${REGRESSION_THRESHOLD}):`);
  for (const r of regressions) {
    console.error(`  ${r.fixture}: ${r.before.toFixed(3)} → ${r.after.toFixed(3)} (-${r.drop.toFixed(3)})`);
  }
  process.exit(1);
}
console.log('✓ no regressions');
