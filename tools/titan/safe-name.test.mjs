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

import { safe as canonical, fixtureStem } from './safe-name.mjs';
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

// ── fixtureStem: the wave-21 subdir-collision fix ─────────────────────────────
//
// extract-fixture.mjs used to flatten css/<section>/<subdir>/<name>.html to
// fixtures/wpt/<section>/<name>.json — 52 A+B-bucket pairs of nested tests
// with equal basenames silently overwrote each other (css-break 14, css-grid
// 13, css-values 14, selectors 1, css-layout-api 10 at the wave-21 pin).
// fixtureStem() is the ONE derivation every stem consumer now shares:
// extract-fixture (fixture filename + component idPrefix), build-combined-
// fixture (component keys + fixture read-back), capture-browser-ref (ref
// cache PNGs), inject-wpt-block (composed testKey + ref lookup), and
// post-load-extract (component-id reconstruction).

test('fixtureStem: top-level tests keep the exact historical bare stem', () => {
  // Depth-3 paths (css/<section>/<name>.html) are the whole pre-fix corpus
  // shape — byte-identical output means ZERO churn for existing fixtures.
  assert.equal(fixtureStem('css/css-color/a98rgb-001.html'), 'a98rgb-001');
  // Dotted stems survive untouched (safe() keeps the dot).
  assert.equal(
    fixtureStem('css/css-break/monolithic-overflow-001.tentative.html'),
    'monolithic-overflow-001.tentative');
  // Depth-2 defensive shape (css/<name>.html) also keeps the bare stem.
  assert.equal(fixtureStem('css/orphan.html'), 'orphan');
});

test('fixtureStem: nested tests encode the subdir chain with __', () => {
  // The exact wave-21 skeptic example pair — distinct stems now.
  assert.equal(
    fixtureStem('css/css-break/flexbox/monolithic-overflow-001.tentative.html'),
    'flexbox__monolithic-overflow-001.tentative');
  assert.equal(
    fixtureStem('css/css-break/grid/monolithic-overflow-001.tentative.html'),
    'grid__monolithic-overflow-001.tentative');
  // Multi-level subdir chains keep every segment, in order.
  assert.equal(
    fixtureStem('css/CSS2/floats-clear/deep/adjoining-float-001.html'),
    'floats-clear__deep__adjoining-float-001');
});

test('fixtureStem: colliding basenames in one section resolve to distinct stems', () => {
  // Subdir vs TOP-LEVEL collisions (css-break has both shapes for the
  // monolithic-overflow family) must diverge too: top-level keeps the bare
  // stem, the nested sibling carries its subdir prefix.
  const top = fixtureStem('css/css-break/monolithic-overflow-001.tentative.html');
  const sub = fixtureStem('css/css-break/flexbox/monolithic-overflow-001.tentative.html');
  assert.notEqual(top, sub);
  assert.equal(top, 'monolithic-overflow-001.tentative');
  assert.equal(sub, 'flexbox__monolithic-overflow-001.tentative');
});

test('fixtureStem: every segment passes through safe() (filesystem-safe)', () => {
  // The current corpus has no unsafe characters (verified at the wave-21
  // pin), but a future re-pin must not be able to smuggle one into a
  // filename — each path segment is sanitised with the canonical class.
  assert.equal(fixtureStem('css/css-x/sub dir/na:me.html'), 'sub_dir__na_me');
  // And the sanitiser is the SAME canonical safe() (dot kept).
  assert.equal(fixtureStem('css/css-x/a.b/c.d.html'), 'a.b__c.d');
});

test('stem-derivation call sites import the shared fixtureStem (no bare-basename copies)', async () => {
  // Source pin in the style of the safe() unification test above: every
  // module that derives a fixture/ref/component stem from a testRel must
  // import fixtureStem from safe-name.mjs, and the historical
  // `basename(<...>, '.html')` flattening derivation must not reappear.
  const sites = [
    'extract-fixture.mjs',       // fixture filename + buildComponents idPrefix
    'build-combined-fixture.mjs',// component keys + fixture read-back
    'capture-browser-ref.mjs',   // ref cache PNG path
    'inject-wpt-block.mjs',      // composed testKey + ref PNG lookup
    'post-load-extract.mjs',     // component-id reconstruction stem
  ];
  for (const rel of sites) {
    const src = await fs.readFile(new URL(`./${rel}`, import.meta.url), 'utf8');
    assert.match(src, /import \{[^}]*fixtureStem[^}]*\} from '\.\/safe-name\.mjs'/,
      `${rel} must import the shared fixtureStem`);
    assert.doesNotMatch(src, /basename\([^)]*,\s*'\.html'\)/,
      `${rel} must not derive a stem by flattening the basename`);
  }
});
