#!/usr/bin/env node
//
// tools/titan/safe-name.test.mjs
//
// Pins that the PNG-name sanitiser is UNIFIED across the whole compare
// pipeline. Historically there were ~5 copies with two divergent character
// classes (one KEPT the dot, one DROPPED it). A WPT test key containing a "."
// sanitised on the producer side (a feeder / the web capture driver) and the
// consumer side (inject-wpt-block's diff glob) with different classes silently
// dropped that platform's column — the exact n/a bug the WPT-fidelity campaign
// fought. These tests assert every compare-side call site now resolves to the
// ONE canonical `safe()` and produces byte-identical output for a dotted key.

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { promises as fs } from 'node:fs';

import { safe as canonical } from './safe-name.mjs';
import { safe as feedLibSafe } from './feed-lib.mjs';
import { safe as injectSafe } from './inject-wpt-block.mjs';
import { safe as splitSafe } from './split-combined-ir.mjs';
import { safeName as iosSafeName, safe as iosSafe } from './feed-ios.mjs';
// feed-lib's composed PNG namer must also flow through the canonical class.
import { composedPngName as androidComposedPng } from './feed-lib.mjs';
import { composedPngName as iosComposedPng } from './feed-ios.mjs';

// ── Every compare-side call site is the SAME function object ──────────────────

test('all compare-side modules re-export the one canonical safe()', () => {
  // Identity, not just equality — they import the single implementation.
  assert.equal(feedLibSafe, canonical);
  assert.equal(injectSafe, canonical);
  assert.equal(splitSafe, canonical);
  assert.equal(iosSafe, canonical);
  assert.equal(iosSafeName, canonical); // feed-ios's historical name is an alias
});

// ── Identical output for a key with a "." and other edge chars ────────────────

test('every call site sanitises a dotted / edge-char key identically', () => {
  // A test key with a dot, a slash, a space and a colon — the chars where the
  // two historical classes diverged. The dot MUST be kept (compare class).
  const key = 'wpt__css-color__a.b/c d:e-001';
  const expected = 'wpt__css-color__a.b_c_d_e-001'; // dot KEPT; / space : → _
  const sites = [canonical, feedLibSafe, injectSafe, splitSafe, iosSafe, iosSafeName];
  for (const fn of sites) assert.equal(fn(key), expected);
  // And they must all agree with each other pairwise (transitively via canonical).
  const outputs = new Set(sites.map((fn) => fn(key)));
  assert.equal(outputs.size, 1, 'all call sites must produce ONE sanitised string');
});

test('canonical safe(): dot kept, other non-safe chars → underscore', () => {
  assert.equal(canonical('a.b'), 'a.b');            // dot preserved
  assert.equal(canonical('a b/c:d'), 'a_b_c_d');    // space / colon → _
  assert.equal(canonical('plain__component'), 'plain__component');
  assert.equal(canonical('keep-dash_and.dot'), 'keep-dash_and.dot');
});

// ── Composed PNG namers (Android + iOS) agree on a dotted test key ────────────

test('android + iOS composedPngName agree for a dotted test key', () => {
  // Android takes the fixture BASENAME (strips .json); iOS takes the testKey.
  // Both must land on the same `<safe(key)>.png` so the on-device write, the
  // feeder poll, and inject's diffComposedVsRef glob all pair up.
  const key = 'wpt__css-values__calc.dotted-001';
  assert.equal(androidComposedPng(`${key}.json`), `${canonical(key)}.png`);
  assert.equal(iosComposedPng(key), `${canonical(key)}.png`);
  assert.equal(androidComposedPng(`${key}.json`), iosComposedPng(key));
});

// ── Source pins: the web capture drivers import the shared module ─────────────
//
// The web drivers used to carry an inline `name.replace(/[^A-Za-z0-9._-]/g,'_')`.
// Assert they now import the shared helper so they can never drift again.

test('web capture drivers import the shared safe() (no inline copy)', async () => {
  for (const rel of ['capture-screenshots.mjs', 'capture-screenshots-hires.mjs']) {
    const src = await fs.readFile(
      new URL(`../../apps/web-harness/${rel}`, import.meta.url), 'utf8');
    assert.match(src, /from '\.\.\/\.\.\/tools\/titan\/safe-name\.mjs'/,
      `${rel} must import the shared sanitiser`);
    assert.doesNotMatch(src, /replace\(\/\[\^A-Za-z0-9\._-\]\/g/,
      `${rel} must not keep an inline safe() copy`);
  }
});
