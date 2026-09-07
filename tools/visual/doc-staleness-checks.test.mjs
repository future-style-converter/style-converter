#!/usr/bin/env node
// Mutation pins for the retrospective additions to
// tools/visual/doc-staleness-check.sh (BACKLOG standing rule: a new check
// must be PROVEN able to fail before it is trusted):
//
//   · newest_corpus_snapshot / check_corpus_current  (A9#9 (a))
//   · check_real_floor                                (A6#16)
//   · check_baseline_orphans                          (A12#4)
//
// Each function is extracted by name from the checker and run in a fresh
// bash against SCRATCH docs — a passing configuration first (the control),
// then one field mutated, asserting the check goes red and names the drift.
// The live tree is deliberately not asserted here: the docs it pins are
// stamped by another lane, and the checker itself reports the live state.
//
// Run via `node --test tools/visual/doc-staleness-checks.test.mjs`.

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdtempSync, mkdirSync, writeFileSync, readFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { resolve, dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const REPO = resolve(__dirname, '..', '..');
const SCRIPT = readFileSync(join(__dirname, 'doc-staleness-check.sh'), 'utf8');

/** Slice `name() { … }` (closing brace at column 0) out of the checker text. */
function bashFn(name) {
  const start = SCRIPT.indexOf(`\n${name}() {`);
  assert.ok(start >= 0, `${name}() not found in doc-staleness-check.sh`);
  const end = SCRIPT.indexOf('\n}\n', start);
  assert.ok(end > start, `${name}() has no column-0 closing brace`);
  return SCRIPT.slice(start + 1, end + 2);
}

/** Run `body` with the named checker functions + the checker's own log/err contract (err sets FAILED=1). */
function check(fnNames, body, env = {}) {
  const prelude = [
    'set -uo pipefail',
    'FAILED=0',
    'log() { echo "[log] $*"; }',
    'warn() { echo "[warn] $*" >&2; }',
    'err() { echo "[err] $*" >&2; FAILED=1; }',
    'CATALOG=558',
    ...fnNames.map(bashFn),
  ].join('\n');
  const r = spawnSync('bash', ['-c', `${prelude}\n${body}\nexit $FAILED`], { encoding: 'utf8', cwd: REPO, env: { ...process.env, ...env } });
  return { status: r.status, out: `${r.stdout}\n${r.stderr}` };
}

const scratch = () => mkdtempSync(join(tmpdir(), 'docstale-'));

// ── newest_corpus_snapshot ──────────────────────────────────────────────────

test('newest_corpus_snapshot picks the highest <M>.<n>, ignores -cal snapshots and sorts numerically', () => {
  const dir = scratch();
  for (const f of ['corpus-v4.json', 'corpus-v6-9.json', 'corpus-v6-15.json', 'corpus-v6-13-cal.json', 'corpus-v6-2.json']) {
    writeFileSync(join(dir, f), '{}');
  }
  const r = check(['newest_corpus_snapshot'], `newest_corpus_snapshot "${dir}"`);
  assert.equal(r.out.trim(), `${dir}/corpus-v6-15.json`);   // not v6-9 (lexical), not -cal
  writeFileSync(join(dir, 'corpus-v7.json'), '{}');
  assert.equal(check(['newest_corpus_snapshot'], `newest_corpus_snapshot "${dir}"`).out.trim(), `${dir}/corpus-v7.json`);
});

// ── check_corpus_current ────────────────────────────────────────────────────

const TOTALS = { totals: { web: { passing: 1205, measured: 1379 }, ios: { passing: 1081, measured: 1366 }, android: { passing: 1058, measured: 1366 } } };
const BACKLOG_OK = 'per wave). Current: **corpus-v6.15**\n(wave 49 — web 1205/1379 87.4%, iOS 1081/1366 79.1%, Android 1058/1366\n77.5%; 32 cells gained).\n';
const STATUS_OK = '**Wave 49 (2026-08-30).** corpus-v6.15: web\n1205/1379 87.4%, iOS 1081/1366 79.1%, Android 1058/1366 77.5%; **32\ncells gained**.\n';

function corpusCase({ backlog = BACKLOG_OK, status = STATUS_OK } = {}) {
  const dir = scratch();
  writeFileSync(join(dir, 'corpus-v6-15.json'), JSON.stringify(TOTALS));
  writeFileSync(join(dir, 'BACKLOG.md'), backlog);
  writeFileSync(join(dir, 'STATUS.md'), status);
  return check(['check_corpus_current'], `check_corpus_current "${dir}/corpus-v6-15.json" "${dir}/BACKLOG.md" "${dir}/STATUS.md"`);
}

test('corpus check: docs that name the snapshot and quote all three fractions pass (control)', () => {
  const r = corpusCase();
  assert.equal(r.status, 0, r.out);
  assert.match(r.out, /live: corpus-v6\.15 → web\/iOS\/Android = 1205\/1379 1081\/1366 1058\/1366/);
});

test('corpus check MUTATION: a stale fraction in BACKLOG goes red and is named', () => {
  const r = corpusCase({ backlog: BACKLOG_OK.replace('1205/1379', '1204/1379') });
  assert.equal(r.status, 1);
  assert.match(r.out, /BACKLOG\.md does not quote 1205\/1379 from corpus-v6\.15/);
});

test('corpus check MUTATION: BACKLOG "Current:" naming the previous snapshot goes red', () => {
  const r = corpusCase({ backlog: BACKLOG_OK.replace('corpus-v6.15', 'corpus-v6.14') });
  assert.equal(r.status, 1);
  assert.match(r.out, /'Current:' line does not name the newest snapshot corpus-v6\.15/);
});

test('corpus check MUTATION: STATUS missing the Android fraction goes red', () => {
  const r = corpusCase({ status: STATUS_OK.replace('1058/1366', '1057/1366') });
  assert.equal(r.status, 1);
  assert.match(r.out, /STATUS\.md does not quote 1058\/1366/);
});

test('corpus check: a snapshot without a totals block is an error, not a pass', () => {
  const dir = scratch();
  writeFileSync(join(dir, 'corpus-v9.json'), '{"_note":"no totals"}');
  writeFileSync(join(dir, 'BACKLOG.md'), BACKLOG_OK);
  writeFileSync(join(dir, 'STATUS.md'), STATUS_OK);
  const r = check(['check_corpus_current'], `check_corpus_current "${dir}/corpus-v9.json" "${dir}/BACKLOG.md" "${dir}/STATUS.md"`);
  assert.equal(r.status, 1);
  assert.match(r.out, /no totals\./);
});

// ── check_real_floor ────────────────────────────────────────────────────────

const AUDIT_JSON = { realTotals: { ir: 558, android: 20, ios: 74, web: 516 }, applierFiles: { android: 61, ios: 119, web: 530 } };
const README_OK = '| Real-applier floor | **Android 20 / 558 · iOS 74 / 558 · Web 516 / 558** | `coverage-audit.mjs` |\n';
const COVERAGE_OK = [
  '**Dedicated `*Applier` files**: Android 61 · iOS 119 · Web 530 (the grouped-applier files inflate this).',
  '| **total** | **558/558** | **20/558** | **558/558** | **74/558** | **558/558** | **516/558** |',
  '',
].join('\n');

function floorCase({ readme = README_OK, coverage = COVERAGE_OK } = {}) {
  const dir = scratch();
  writeFileSync(join(dir, 'audit.json'), JSON.stringify(AUDIT_JSON));
  writeFileSync(join(dir, 'README.md'), readme);
  writeFileSync(join(dir, 'COVERAGE.md'), coverage);
  return check(['check_real_floor'], `check_real_floor "${dir}/audit.json" "${dir}/README.md" "${dir}/COVERAGE.md"`);
}

test('real-floor check: README row + COVERAGE total row + dedicated line matching the audit pass (control)', () => {
  const r = floorCase();
  assert.equal(r.status, 0, r.out);
});

test('real-floor check MUTATION: README quoting Android 19 (the live stale value) goes red', () => {
  const r = floorCase({ readme: README_OK.replace('Android 20', 'Android 19') });
  assert.equal(r.status, 1);
  assert.match(r.out, /README\.md: real-applier floor row is not 'Android 20 \/ 558/);
});

test('real-floor check MUTATION: a pre-campaign COVERAGE.md total row goes red', () => {
  const r = floorCase({ coverage: COVERAGE_OK.replace('**20/558**', '**18/558**') });
  assert.equal(r.status, 1);
  assert.match(r.out, /COVERAGE\.md: total row does not carry real 20\/74\/516 of 558/);
});

test('real-floor check MUTATION: stale dedicated-file counts go red', () => {
  const r = floorCase({ coverage: COVERAGE_OK.replace('Android 61', 'Android 59') });
  assert.equal(r.status, 1);
  assert.match(r.out, /dedicated \*Applier file counts are not 'Android 61 · iOS 119 · Web 530'/);
});

// ── check_baseline_orphans ──────────────────────────────────────────────────

function orphanCase({ declareBar = true } = {}) {
  const dir = scratch();
  const baseline = join(dir, 'baseline'); mkdirSync(baseline);
  const fixtures = join(dir, 'fixtures', 'sub'); mkdirSync(fixtures, { recursive: true });
  for (const f of ['iOS__000_Foo.png', 'Android__000_Foo.png', 'web__001_Bar.png', 'simulator.png']) writeFileSync(join(baseline, f), '');
  const doc = { components: { Foo: { properties: {}, children: declareBar ? { Bar: { properties: {} } } : {} } } };
  writeFileSync(join(fixtures, 'a.json'), JSON.stringify(doc));
  writeFileSync(join(fixtures, 'broken.json'), '{ not json');   // unparseable fixtures are skipped, not fatal
  return check(['check_baseline_orphans'], `check_baseline_orphans "${baseline}" "${join(dir, 'fixtures')}"`);
}

test('orphan check: every baseline names a declared component (nested children count) → pass (control)', () => {
  const r = orphanCase();
  assert.equal(r.status, 0, r.out);
  assert.match(r.out, /all 3 committed baseline PNGs name a component/);   // simulator.png is not a baseline
});

test('orphan check MUTATION: retiring a component while its PNG survives goes red and names it', () => {
  const r = orphanCase({ declareBar: false });
  assert.equal(r.status, 1);
  assert.match(r.out, /orphan baselines .* components declared by NO fixture .*: Bar/);
});
