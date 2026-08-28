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
import { mkdtempSync, mkdirSync, copyFileSync, writeFileSync, readdirSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { resolve, dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

import { EXIT_UNEXPECTED_DIVERGENCE, EXIT_STALE_EXPECTATION } from './cross-platform-gate.mjs';

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
  writeFileSync(ledger, JSON.stringify({ expectations }, null, 2));
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
