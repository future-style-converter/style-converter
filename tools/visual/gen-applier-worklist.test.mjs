#!/usr/bin/env node
//
// Pins for tools/visual/gen-applier-worklist.mjs + the committed
// applier-worklist.json — the campaign checklist both humans and wave
// orchestration read. These protect the two things a refactor is most
// likely to break: the naming rule (fixture mapping silently drops rows)
// and the bucket invariants (the honest-accounting contract).

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { promises as fs } from 'node:fs';

const src = await fs.readFile(new URL('./gen-applier-worklist.mjs', import.meta.url), 'utf8');
const worklist = JSON.parse(await fs.readFile(new URL('./applier-worklist.json', import.meta.url), 'utf8'));

test('kebab rule: the generator handles the ZIndex consecutive-capitals case', () => {
  // The ONLY name in the 550-catalogue with consecutive capitals. If the
  // second replace (uppercase followed by uppercase-then-lowercase) is
  // dropped, ZIndex → "zindex" and the z-index fixture silently unmaps.
  assert.match(src, /\(\[A-Z\]\)\(\[A-Z\]\[a-z\]\)/, 'consecutive-capitals kebab rule dropped');
});

test('committed worklist: 550 rows, buckets sum, tracker counts intact', () => {
  assert.equal(worklist.properties.length, 550);
  const sum = Object.values(worklist.summary.byBucket).reduce((a, b) => a + b, 0);
  assert.equal(sum, 550, 'bucket counts must partition the catalogue');
  // The recovered tracker's converged headline is the worklist's spine:
  // verified 91 and failing 3 must never drift silently (wontfix absorbs
  // from blocked/exhausted, so those two are the invariant pair).
  assert.equal(worklist.summary.byBucket['verified'], 91);
  assert.equal(worklist.summary.byBucket['failing-real-divergence'], 3);
});

test('committed worklist: real-applier floor matches coverage-audit', () => {
  // Same rule, independently computed — if these drift, one of the two
  // scanners changed its matching rules without the other.
  assert.equal(worklist.summary.realApplier.android, 18);
  assert.equal(worklist.summary.realApplier.ios, 73);
  assert.equal(worklist.summary.realApplier.web, 508);
});

test('generator re-verifies the recovered tracker counts (wrong-ref guard)', () => {
  // The tracker is read from git history at a pinned ref; if the ref or the
  // row regex drifts, the generator must FAIL LOUDLY, not emit garbage.
  assert.match(src, /FATAL: recovered tracker has/, 'tracker count re-verification dropped');
  assert.match(src, /passing: 91/, 'expected tracker counts dropped');
});
