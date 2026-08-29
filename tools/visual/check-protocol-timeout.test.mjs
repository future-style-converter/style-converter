#!/usr/bin/env node
// Tests for check-protocol-timeout.mjs (LANE E finding 2, 2026-08-29).
//
// The lie being killed: doc-staleness-check.sh Pattern 10 guarded the
// round-75 puppeteer fix with `grep -q protocolTimeout <file>` — satisfied
// by the fix's own COMMENT. The central test here performs that exact
// mutation on the REAL shipping files: delete the `protocolTimeout:` option
// from the launch call, keep the comment, and assert (a) the old grep-style
// check still passes (documenting the hole) while (b) the new checker
// fails. Controls in both directions: the unmutated real files must PASS,
// so the checker can't be satisfied by rejecting everything.
//
// Run via `node --test tools/visual/check-protocol-timeout.test.mjs`.

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

import { checkSource, stripCommentsAndStrings, findLaunchCalls } from './check-protocol-timeout.mjs';

const __dirname = dirname(fileURLToPath(import.meta.url));
// The two files Pattern 10 pins — the round-75 fix lives in their
// puppeteer.launch options objects.
const REAL_FILES = ['interaction-states.mjs', 'a11y-audit.mjs']
  .map((f) => resolve(__dirname, f));

// ── Controls: the shipping files, unmutated, must verify ───────────────────
test('real files pass: launch() carries protocolTimeout ≥ 5 min', () => {
  for (const f of REAL_FILES) {
    const r = checkSource(readFileSync(f, 'utf8'), { expectLaunch: true });
    // Each file launches puppeteer exactly where its comment says it does…
    assert.ok(r.launches >= 1, `${f}: expected a launch() call, found none`);
    // …and the option is present, arithmetic, and at/above the floor.
    assert.deepEqual(r.problems, [], `${f}: ${r.problems.join('; ')}`);
  }
});

// ── The mutation that fooled the grep ──────────────────────────────────────
test('deleting the option (keeping the comment) fails the new check but passed the old grep', () => {
  for (const f of REAL_FILES) {
    const src = readFileSync(f, 'utf8');
    // The exact regression: remove `protocolTimeout: <expr>` (with its
    // separating comma/space) from the launch options object, touching
    // NOTHING else — every comment mentioning protocolTimeout survives.
    const mutated = src.replace(/,?\s*protocolTimeout\s*:\s*[^,}]+/g, '');
    // Sanity: the mutation actually removed an option (test must not
    // vacuously pass on a no-op replace).
    assert.notEqual(mutated, src, `${f}: mutation did not apply`);
    // (a) The OLD check — word present anywhere in the file — still greens,
    // because the round-75 comments mention protocolTimeout. This is the
    // documented hole; if this assertion ever fails the comments were
    // removed and the old check would have worked by accident.
    assert.ok(mutated.includes('protocolTimeout'), `${f}: comment vanished — mutation no longer demonstrates the grep hole`);
    // (b) The NEW check fails: the launch call no longer configures it.
    const r = checkSource(mutated, { expectLaunch: true });
    assert.ok(
      r.problems.some((p) => p.includes('does not pass protocolTimeout')),
      `${f}: checker missed the removed option (problems: ${r.problems.join('; ') || 'none'})`
    );
  }
});

// ── Value floor: explicitly-set-but-too-low must fail ──────────────────────
test('protocolTimeout below the 5-minute floor fails', () => {
  // 1000 ms is explicit configuration, so the presence-only check would
  // pass it — but it re-creates the round-75 hang with extra steps.
  const src = `import p from 'puppeteer';\nconst b = await p.launch({ headless: true, protocolTimeout: 1000 });`;
  const r = checkSource(src);
  assert.ok(r.problems.some((p) => p.includes('below the required')), r.problems.join('; '));
  // Control: the same call at the real value (arithmetic form) passes.
  const ok = checkSource(src.replace('1000', '5 * 60 * 1000'));
  assert.deepEqual(ok.problems, []);
  // Numeric-separator form (300_000, the tools/titan house style) also
  // evaluates — the charset gate must not reject underscores.
  const sep = checkSource(src.replace('1000', '300_000'));
  assert.deepEqual(sep.problems, []);
});

// ── Prose can never vouch for code ─────────────────────────────────────────
test('comment-only and string-only mentions do not satisfy the check', () => {
  // Comment mention + bare launch: the original grep-green regression state.
  const commentOnly = `// Round 75: bump protocolTimeout to 5 minutes\nconst b = await p.launch({ headless: true });`;
  assert.ok(checkSource(commentOnly).problems.some((p) => p.includes('does not pass')));
  // String mention inside the args must not count either — the scrubber
  // blanks string interiors before the option regex runs.
  const stringOnly = `const b = await p.launch({ headless: true, note: 'protocolTimeout: 999999' });`;
  assert.ok(checkSource(stringOnly).problems.some((p) => p.includes('does not pass')));
});

// ── Non-verifiable values fail loudly, never silently ──────────────────────
test('an identifier value is a failure, not a pass', () => {
  // `protocolTimeout: TIMEOUT_MS` may be perfectly correct at runtime, but
  // a static pin cannot vouch for it — per the repo's no-silent-fallthrough
  // rule that is a red result demanding a human, not a shrug.
  const src = `const b = await p.launch({ protocolTimeout: TIMEOUT_MS });`;
  assert.ok(checkSource(src).problems.some((p) => p.includes('not statically verifiable')));
});

// ── expectLaunch: a vanished launch call cannot silently pass ──────────────
test('--expect-launch fails a file with no launch() at all', () => {
  // Pattern 10's files exist to drive puppeteer; if a refactor removes the
  // call the guard must demand review rather than report success.
  const r = checkSource(`export const x = 1; // no browser here`, { expectLaunch: true });
  assert.ok(r.problems.some((p) => p.includes('no launch() call')));
  // Control: without the flag, a launch-free file is legitimately clean.
  assert.deepEqual(checkSource(`export const x = 1;`).problems, []);
});

// ── Scrubber unit checks (the load-bearing precondition) ───────────────────
test('scrubber blanks comments/strings but preserves code and offsets', () => {
  const src = `// protocolTimeout in a comment\nconst s = "protocolTimeout";\nlaunch({ protocolTimeout: 1 })`;
  const out = stripCommentsAndStrings(src);
  // Same length — offsets stay valid for line-number reporting.
  assert.equal(out.length, src.length);
  // Comment and string interiors are gone; the real option key survives.
  assert.equal((out.match(/protocolTimeout/g) ?? []).length, 1);
  // And the surviving mention is the one inside the launch args.
  assert.ok(/launch\(\{ protocolTimeout: 1 \}\)/.test(out));
});

test('multi-line launch options object is captured whole', () => {
  // tools/titan house style spreads the options across lines — the
  // balanced-paren walk must span them.
  const src = `const b = await puppeteer.launch({\n  headless: true,\n  protocolTimeout: 300_000, // long-run safety margin\n});`;
  const calls = findLaunchCalls(stripCommentsAndStrings(src));
  assert.equal(calls.length, 1);
  assert.deepEqual(checkSource(src, { expectLaunch: true }).problems, []);
});
