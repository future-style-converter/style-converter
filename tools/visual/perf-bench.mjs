#!/usr/bin/env node
// Tier 10 — Performance benchmarks.
//
// Generate large component trees (100, 1000, 10000 elements), render via
// each platform's harness, measure render time + memory.
//
// History note: the previous impl used `execSync(..., { stdio: 'pipe' })`
// which buffers child stdout into memory up to maxBuffer (default 1 MB).
// test-all.sh on a 10 000-element tree emits ~3 MB of compare-screenshots
// chatter, so the 10k bench always died with ENOBUFS before producing a
// timing. Round 41 fix: use spawnSync with stdio: ['ignore','ignore','pipe']
// so stdout streams to /dev/null (we only need wall-clock + exit code) and
// stderr is still captured for error reporting.

import { writeFileSync, existsSync, mkdirSync } from 'node:fs';
import { spawnSync } from 'node:child_process';

const SIZES = [100, 1000, 10000];
const OUT_DIR = 'fixtures/perf';
if (!existsSync(OUT_DIR)) mkdirSync(OUT_DIR, { recursive: true });

// Per-platform-skip env var: lets a CI run skip the slowest platform without
// re-shelling test-all.sh. We default to all-platforms-on; CI can flip via
// SKIP_IOS=1 etc. (test-all.sh already honours these — we just pass through).
const ENV_FORWARD = ['SKIP_IOS', 'SKIP_ANDROID', 'SKIP_WEB', 'WEB_PORT'].reduce(
  (acc, k) => { if (process.env[k]) acc[k] = process.env[k]; return acc; },
  {}
);

// Generate fixture with N components — each a simple styled box.
// Background colors cycle through 4 options so the IR isn't trivially
// deduplicable; fixture-cache hits would otherwise undercount the work.
for (const size of SIZES) {
  const components = {};
  for (let i = 0; i < size; i++) {
    components[`Box_${i}`] = {
      properties: {
        width: '40px',
        height: '40px',
        margin: '2px',
        'background-color': ['#ef4444', '#10b981', '#3b82f6', '#f59e0b'][i % 4],
      },
    };
  }
  writeFileSync(`${OUT_DIR}/tree_${size}.json`, JSON.stringify({ components }, null, 2));
  console.log(`generated tree_${size}.json (${size} components)`);
}

// Snapshot Node-side RSS before each run so we can report a delta.
// (test-all.sh spawns gradle/iOS-sim/Android-emulator/vite, all in
// separate processes, so this measures only the Node bench harness's
// own footprint — useful for catching memory leaks in repeated runs.)
function rssMB() { return Math.round(process.memoryUsage().rss / 1024 / 1024); }

const results = { generated: new Date().toISOString(), runs: {} };
for (const size of SIZES) {
  const fp = `${OUT_DIR}/tree_${size}.json`;
  console.log(`benchmarking ${size}…  (rss=${rssMB()}MB before)`);
  const start = Date.now();

  // spawnSync with stdout dropped to keep us under any buffer cap. We still
  // pipe stderr so a real failure (gradle crash, simulator boot timeout)
  // shows up in the report. 30-min timeout is generous; the 10 000-element
  // tree on a cold machine has been observed at ~17 min wall.
  const result = spawnSync('./test-all.sh', [fp], {
    stdio: ['ignore', 'ignore', 'pipe'],
    timeout: 30 * 60 * 1000,
    env: { ...process.env, ...ENV_FORWARD },
  });
  const wall_ms = Date.now() - start;

  if (result.error) {
    // Spawn-level failure (e.g. ENOENT on test-all.sh, ETIMEDOUT). Distinct
    // from "test-all.sh ran and returned non-zero" — different failure modes
    // have different remediations.
    results.runs[size] = {
      wall_ms,
      status: 'spawn-failed',
      error: result.error.code || result.error.message,
      rss_after_mb: rssMB(),
    };
  } else if (result.status !== 0) {
    results.runs[size] = {
      wall_ms,
      status: 'failed',
      exit_code: result.status,
      stderr_tail: result.stderr?.toString().split('\n').slice(-20).join('\n') || '',
      rss_after_mb: rssMB(),
    };
  } else {
    results.runs[size] = {
      wall_ms,
      status: 'ok',
      rss_after_mb: rssMB(),
      ms_per_element: Math.round((wall_ms / size) * 1000) / 1000,
    };
  }
  const r = results.runs[size];
  console.log(`  ${size}: ${r.wall_ms}ms (${r.status}${r.ms_per_element ? `, ${r.ms_per_element}ms/elem` : ''}, rss=${r.rss_after_mb}MB)`);
}

writeFileSync('tools/visual/perf-results.json', JSON.stringify(results, null, 2));
console.log(`\n✓ wrote tools/visual/perf-results.json`);
console.log(JSON.stringify(results, null, 2));
