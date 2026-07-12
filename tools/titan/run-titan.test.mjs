#!/usr/bin/env node
//
// Pins for tools/titan/run-titan.sh — the smoke-100 orchestrator.
//
// run-titan.sh is bash, so these are source-scan pins in the style of the
// stale-path pins in inject-wpt-block.test.mjs / render-titan.test.mjs:
// they assert the load-bearing lines a refactor is most likely to drop.
//
// Defect 4 history: run-titan.sh predated the WPT_MODE placeholder-text
// fix — its test-all.sh capture ran WITHOUT the flag, so placeholder name
// text rendered into every capture and contaminated the browser-ref
// diffs into false structural-divergence (the pilot-001 signature).
// section-runner.sh Step 5 has carried the flag since the fix; run-titan
// now sets it in BOTH platform-scope branches.

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { promises as fs } from 'node:fs';

const src = await fs.readFile(new URL('./run-titan.sh', import.meta.url), 'utf8');

test('both capture branches export WPT_MODE=1 (defect 4 stays fixed)', () => {
  // One occurrence per platform-scope branch (web-only + all).
  const hits = src.match(/^\s*WPT_MODE=1 \\$/gm) ?? [];
  assert.ok(hits.length >= 2, `expected WPT_MODE=1 in both capture branches, found ${hits.length}`);
});

test('capture still delegates through test-all.sh with the shared-lock escape', () => {
  // The WPT_MODE fix must not displace the lock passthrough — both are
  // required for an honest, non-colliding capture.
  assert.match(src, /TESTALL_SKIP_LOCK=1/, 'lock passthrough dropped');
  assert.match(src, /\.\/test-all\.sh "\$REL_INPUT"/, 'test-all.sh delegation dropped');
});

test('web-only scope still wipes stale native screenshots before capture', () => {
  // Stale iOS/Android PNGs merge into the manifest as web=missing rows and
  // corrupt the classifier distribution — the guard must survive edits.
  assert.match(src, /rm -rf "\$PROJECT_ROOT\/apps\/ios-harness\/screenshots" "\$PROJECT_ROOT\/apps\/android-harness\/screenshots"/);
});
