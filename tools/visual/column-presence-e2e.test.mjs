#!/usr/bin/env node
// End-to-end tests for the comparator's COLUMN-PRESENCE behaviour under
// `--baseline` (retrospective A9#0 — the comparator half of BACKLOG #5).
//
// The hole, executed by the audit before these checks existed: iOS + web
// capture dirs holding the committed baselines, the Android dir EMPTY →
// `--baseline` printed "✓ no regressions vs baseline (284 platform-comparisons
// ran)" and exited 0; Android holding 10 of 142 → the same verdict plus 16
// "ledger pair not exercised" warnings. Two columns green-lit the run.
//
// Recipe (the audit's "colhole", subsampled): committed baselines ARE valid
// captures of themselves, so copying `{platform}__{NNN}_{Name}.png` into a
// capture dir as `{NNN}_{Name}.png` yields a run that must pass against the
// baseline exactly. Removing a column, or part of one, is then the mutation.
// The cross-platform gate is disabled for these runs so the ledger (its
// contents and its schema) cannot interfere with what is being measured.
//
// Run via `node --test tools/visual/column-presence-e2e.test.mjs`.

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdtempSync, mkdirSync, copyFileSync, readdirSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { resolve, dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const BASELINE = resolve(__dirname, 'baseline');
const COMPARATOR = resolve(__dirname, 'compare-screenshots.mjs');
const PLATFORMS = ['iOS', 'Android', 'web'];
const N = 5;                                             // components per column — enough to be partial, cheap to score

/** The first N component names that have a committed baseline on ALL THREE platforms. */
function pickTrio(n) {
  const byName = new Map();
  for (const f of readdirSync(BASELINE)) {
    const m = f.match(/^(iOS|Android|web)__(\d+_.+\.png)$/);
    if (!m) continue;
    if (!byName.has(m[2])) byName.set(m[2], new Set());
    byName.get(m[2]).add(m[1]);
  }
  const trio = [...byName.entries()].filter(([, s]) => s.size === 3).map(([name]) => name).sort();
  assert.ok(trio.length >= n, `need ≥${n} components with baselines on all three platforms, found ${trio.length}`);
  return trio.slice(0, n);
}

/** Three capture dirs, each holding its platform's baseline copy of the listed names. */
function scaffold(columns) {
  const dir = mkdtempSync(join(tmpdir(), 'colpresence-e2e-'));
  const dirs = {};
  for (const p of PLATFORMS) {
    dirs[p] = join(dir, p);
    mkdirSync(dirs[p]);
    for (const name of columns[p] ?? []) copyFileSync(join(BASELINE, `${p}__${name}`), join(dirs[p], name));
  }
  return { dir, dirs };
}

/** Spawn the real comparator in --baseline mode; SKIP_* come ONLY from `env`. */
function run(s, env = {}) {
  const e = {
    ...process.env,
    IOS_SCREENSHOTS_DIR: s.dirs.iOS,
    ANDROID_SCREENSHOTS_DIR: s.dirs.Android,
    WEB_SCREENSHOTS_DIR: s.dirs.web,
    REPORT_DIR: join(s.dir, 'report'),
    MANIFEST_OUT: join(s.dir, 'manifest.json'),
  };
  // A SKIP_* inherited from the invoking shell would silently change the
  // verdict under test — strip them, then apply exactly what the test says.
  for (const k of ['SKIP_IOS', 'SKIP_ANDROID', 'SKIP_WEB']) delete e[k];
  Object.assign(e, env);
  const r = spawnSync(process.execPath,
    [COMPARATOR, '--input', 'fixtures/visual-test.json', '--baseline', '--no-cross-platform-gate'],
    { encoding: 'utf8', env: e });
  return { status: r.status, out: `${r.stdout}\n${r.stderr}` };
}

const names = pickTrio(N);

test('control: three full columns pass against their own baselines (exit 0)', () => {
  // If this failed, every assertion below would be measuring a broken
  // scaffold rather than the column-presence checks.
  const s = scaffold({ iOS: names, Android: names, web: names });
  const { status, out } = run(s);
  assert.equal(status, 0, out);
  assert.match(out, new RegExp(`no regressions vs baseline \\(${N * 3} platform-comparisons ran\\)`));
});

test('a platform with baselines but ZERO captures fails with exit 2 (the colhole)', () => {
  // The audit's exact shape: iOS + web present, Android dir empty, no SKIP.
  const s = scaffold({ iOS: names, Android: [], web: names });
  const { status, out } = run(s);
  assert.equal(status, 2, out);
  assert.match(out, new RegExp(`✗ Android: ${N} committed baseline\\(s\\) match this fixture's components`));
  assert.match(out, /produced ZERO Android captures and SKIP_ANDROID=1 was not set/);
  assert.doesNotMatch(out, /no regressions vs baseline/, 'the two remaining columns must not green-light the run');
});

test('the same empty column with SKIP_ANDROID=1 is a deliberate skip and passes', () => {
  // The escape hatch is the SAME knob test-all.sh and CI already use — a
  // one-platform-per-job CI run stays untouched by construction.
  const s = scaffold({ iOS: names, Android: [], web: names });
  const { status, out } = run(s, { SKIP_ANDROID: '1' });
  assert.equal(status, 0, out);
  assert.match(out, new RegExp(`no regressions vs baseline \\(${N * 2} platform-comparisons ran\\)`));
});

test('a PARTIAL column fails: every baseline the column skipped is a regression (exit 1)', () => {
  // colhole2: Android holding 2 of 5. The old compareBaseline `continue`d on
  // "baseline exists, capture missing" and the row passed on its other two
  // platforms.
  const s = scaffold({ iOS: names, Android: names.slice(0, 2), web: names });
  const { status, out } = run(s);
  assert.equal(status, 1, out);
  assert.match(out, /the Android column is PARTIAL/);
  const missing = N - 2;
  assert.match(out, new RegExp(`${missing} component\\(s\\) regressed beyond thresholds \\(${N * 2 + 2} platform-comparisons ran\\)`));
  for (const name of names.slice(2)) {
    assert.match(out, new RegExp(`Android/${name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}: baseline exists but this run has no usable Android capture`));
  }
});

test('a partial column is NOT excused by SKIP_ANDROID=1 — the knob covers an absent column, not a short one', () => {
  // SKIP_* says "I did not run this platform". A platform that DID run and
  // dropped components has failed, whatever the environment claims.
  const s = scaffold({ iOS: names, Android: names.slice(0, 2), web: names });
  const { status, out } = run(s, { SKIP_ANDROID: '1' });
  assert.equal(status, 1, out);
});
