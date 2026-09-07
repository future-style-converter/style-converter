#!/usr/bin/env node
// End-to-end tests for the cross-platform gate's EXIT CODES.
//
// cross-platform-gate.test.mjs unit-tests the decision function. That is not
// the same as proving the gate fails the build — a gate can classify every
// row perfectly and still exit 0 because nobody wired the constant to
// process.exit. This repo has already shipped several "gates" that computed
// a verdict and gated nothing (the cross-platform pairs themselves were
// advisory for the entire life of the project), so the wiring is exactly the
// part worth testing.
//
// These tests spawn the real comparator against real PNGs and assert on its
// exit status, using CROSS_PLATFORM_EXPECTATIONS to supply a ledger.
//
// Run via `node --test tools/visual/cross-platform-gate-e2e.test.mjs`.

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdtempSync, mkdirSync, copyFileSync, writeFileSync, readdirSync, readFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { resolve, dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

import { EXIT_UNEXPECTED_DIVERGENCE, EXIT_STALE_EXPECTATION, validateLedger } from './cross-platform-gate.mjs';

const __dirname = dirname(fileURLToPath(import.meta.url));
const BASELINE = resolve(__dirname, 'baseline');
const COMPARATOR = resolve(__dirname, 'compare-screenshots.mjs');

// ── Scaffolding ─────────────────────────────────────────────────────────────

/**
 * Build a run directory: three platform capture dirs plus a ledger.
 *
 * `mode: 'agree'` copies the SAME source PNG into every platform, so all
 * three pairs are byte-identical and every pair passes. `mode: 'diverge'`
 * gives iOS a different image, so its two pairs fail. Divergence is created
 * from two genuinely different baseline captures rather than by synthesising
 * noise, so the metrics behave like real ones.
 */
function scaffold(mode, expectations) {
  const dir = mkdtempSync(join(tmpdir(), 'xgate-e2e-'));
  const dirs = {};
  for (const p of ['iOS', 'Android', 'web']) {
    dirs[p] = join(dir, p);
    mkdirSync(dirs[p]);
  }

  // Two visibly different real captures: same component, two platforms.
  const files = readdirSync(BASELINE);
  const same  = files.find((f) => f.startsWith('Android__'));
  const other = files.find((f) => f.startsWith('iOS__') &&
                                  f.slice(5) !== same.slice(9)) ?? files.find((f) => f.startsWith('iOS__'));
  assert.ok(same && other, 'baseline PNGs must exist for this test to mean anything');

  const NAME = '000_Case.png';
  copyFileSync(join(BASELINE, same), join(dirs.Android, NAME));
  copyFileSync(join(BASELINE, same), join(dirs.web, NAME));
  copyFileSync(join(BASELINE, mode === 'diverge' ? other : same), join(dirs.iOS, NAME));

  const ledger = join(dir, 'ledger.json');
  // The ledger SCHEMA (validateLedger, retrospective A9#5/A12#8) now demands
  // an owner and an expiry on every line — a schema-invalid ledger is exit 2
  // before any pair is judged. These tests are about the GATE's semantics,
  // so the scaffold fills in the contract fields an entry omits; the schema
  // has its own tests (unit in cross-platform-gate.test.mjs, exit-2 e2e
  // below). Fields an entry states explicitly win.
  const complete = expectations.map((e) => ({ owner: 'test', expires: '2099-01-01', ...e }));
  writeFileSync(ledger, JSON.stringify({ expectations: complete }, null, 2));
  return { dir, dirs, ledger, NAME };
}

/** Run the comparator over a scaffolded run; return {status, out}. */
function runGate({ dirs, ledger }) {
  const r = spawnSync(process.execPath, [COMPARATOR, '--input', 'fixtures/e2e.json'], {
    encoding: 'utf8',
    env: {
      ...process.env,
      IOS_SCREENSHOTS_DIR: dirs.iOS,
      ANDROID_SCREENSHOTS_DIR: dirs.Android,
      WEB_SCREENSHOTS_DIR: dirs.web,
      REPORT_DIR: join(dirs.iOS, '..', 'report'),
      CROSS_PLATFORM_EXPECTATIONS: ledger,
    },
  });
  return { status: r.status, out: `${r.stdout}\n${r.stderr}` };
}

// ── The wiring ──────────────────────────────────────────────────────────────

test('three agreeing platforms with an empty ledger exit 0', () => {
  // The control. If this failed, every other assertion here would be
  // measuring a broken scaffold rather than the gate.
  const s = scaffold('agree', []);
  const { status, out } = runGate(s);
  assert.equal(status, 0, `expected clean exit, got ${status}:\n${out}`);
});

test('an unexpected divergence exits EXIT_UNEXPECTED_DIVERGENCE', () => {
  const s = scaffold('diverge', []);
  const { status, out } = runGate(s);
  assert.equal(status, EXIT_UNEXPECTED_DIVERGENCE, out);
  assert.match(out, /unexpected cross-platform divergence/);
});

test('a listed divergence is excused and exits 0', () => {
  const s = scaffold('diverge', [
    { component: '000_Case.png', pair: 'iOS-Android', reason: 'known', owner: 'test' },
    { component: '000_Case.png', pair: 'iOS-web', reason: 'known', owner: 'test' },
  ]);
  const { status, out } = runGate(s);
  assert.equal(status, 0, out);
});

// ── The promotion this file exists to pin ───────────────────────────────────

test('a stale expectation FAILS with EXIT_STALE_EXPECTATION', () => {
  // The behaviour change the noise-floor study unlocked. Everything agrees,
  // so the listed pair passes — the excuse outlived the divergence.
  const s = scaffold('agree', [
    { component: '000_Case.png', pair: 'iOS-Android', reason: 'fixed long ago', owner: 'test' },
  ]);
  const { status, out } = runGate(s);
  assert.equal(status, EXIT_STALE_EXPECTATION, out);
  assert.match(out, /now passing, delete the line/);
});

test('an orphaned expectation FAILS and says which component is gone', () => {
  const s = scaffold('agree', [
    { component: 'NoSuchComponent.png', pair: 'iOS-Android', reason: 'r', owner: 'test' },
  ]);
  const { status, out } = runGate(s);
  assert.equal(status, EXIT_STALE_EXPECTATION, out);
  assert.match(out, /no such component/);
  assert.match(out, /NoSuchComponent/);
});

test('unexpected divergence outranks a stale entry', () => {
  // Both conditions at once. "The runtimes disagree" is the product
  // problem; a stale line is bookkeeping. The exit code must name the
  // former, or a real regression gets reported as a tidy-up chore.
  const s = scaffold('diverge', [
    { component: '000_Case.png', pair: 'Android-web', reason: 'stale — this pair agrees', owner: 'test' },
  ]);
  const { status, out } = runGate(s);
  assert.equal(status, EXIT_UNEXPECTED_DIVERGENCE, out);
});

test('the two failure codes are distinct', () => {
  // A collision would make "ledger needs cleaning" indistinguishable from
  // "a runtime regressed" to every caller that branches on $?.
  assert.notEqual(EXIT_STALE_EXPECTATION, EXIT_UNEXPECTED_DIVERGENCE);
  assert.equal(EXIT_STALE_EXPECTATION, 5);
});

// ── The ledger schema (retrospective A9#5 / A12#8) ───────────────────────────

test('a schema-invalid ledger exits 2 and names the offending file:line', () => {
  // The wave-1 seed carried owner "unassigned" on 29/29 lines for 49 waves
  // and nothing rejected it. Now the loader refuses to judge a single pair
  // against a ledger that violates its own contract — and points at the line.
  const s = scaffold('diverge', [
    { component: '000_Case.png', pair: 'iOS-Android', reason: 'known', owner: 'unassigned' },
    { component: '000_Case.png', pair: 'iOS-web', reason: 'known' },   // owner + expires filled by the scaffold → valid
  ]);
  const { status, out } = runGate(s);
  assert.equal(status, 2, out);
  assert.match(out, /violates the ledger contract \(1 problem\(s\)\)/);
  assert.match(out, /ledger\.json:\d+ — `owner` is the placeholder "unassigned"/);
  // It must fail as a SETUP error, never as a verdict: no exit 4/5 text.
  assert.doesNotMatch(out, /unexpected cross-platform divergence/);
});

test('a ledger line whose expiry is unparseable or absent exits 2', () => {
  const s = scaffold('diverge', [
    { component: '000_Case.png', pair: 'iOS-Android', reason: 'known', owner: 'lane-x', expires: 'someday' },
  ]);
  const { status, out } = runGate(s);
  assert.equal(status, 2, out);
  assert.match(out, /`expires` is not a parseable date: "someday"/);
});

test('the COMMITTED ledger honours its own contract', () => {
  // The pin the retrospective asked for: every line of
  // tools/visual/cross-platform-expectations.json carries a real owner and a
  // parseable, non-dead expiry. Red exactly while any line still says
  // "unassigned" — which is the point: the gate itself exits 2 on that
  // ledger, so this test failing first is the cheaper place to learn it.
  const path = resolve(__dirname, 'cross-platform-expectations.json');
  const raw = readFileSync(path, 'utf8');
  const problems = validateLedger(JSON.parse(raw), raw);
  assert.deepEqual(
    problems.map((p) => `${path}:${p.line} — ${p.message}`),
    [],
    'the committed ledger has schema problems (assign owners / fix expiries):',
  );
});

test('--no-cross-platform-gate suppresses the stale failure', () => {
  // The documented escape hatch must actually work, or a mid-refactor run
  // has no way through and people start deleting entries to get green.
  const s = scaffold('agree', [
    { component: '000_Case.png', pair: 'iOS-Android', reason: 'r', owner: 'test' },
  ]);
  const r = spawnSync(process.execPath,
    [COMPARATOR, '--input', 'fixtures/e2e.json', '--no-cross-platform-gate'], {
      encoding: 'utf8',
      env: {
        ...process.env,
        IOS_SCREENSHOTS_DIR: s.dirs.iOS,
        ANDROID_SCREENSHOTS_DIR: s.dirs.Android,
        WEB_SCREENSHOTS_DIR: s.dirs.web,
        REPORT_DIR: join(s.dirs.iOS, '..', 'report2'),
        CROSS_PLATFORM_EXPECTATIONS: s.ledger,
      },
    });
  assert.equal(r.status, 0, `${r.stdout}\n${r.stderr}`);
});
