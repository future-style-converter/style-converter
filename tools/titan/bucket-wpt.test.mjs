#!/usr/bin/env node
//
// Unit tests for tools/titan/bucket-wpt.mjs.
//
// Scope (wave-30 A3): the CharacterData-mutation SUPPLEMENT this file layers
// on top of wpt-not-applicable.mjs's Rule 4, plus the entry-point guard that
// makes importing the module possible at all. The A/B/C classifier itself is
// exercised by its on-disk output (wpt-buckets.json) and the corpus runs; it
// is not re-pinned here.
//
// Pattern mirrors extract-fixture.test.mjs — node:test, pure helpers only, no
// corpus access, so the suite stays hermetic and sub-100 ms.

import { test } from 'node:test';
import assert from 'node:assert/strict';

// Importing this module at all is the first thing under test: before wave-30
// the file called main() unconditionally, so any import walked ~24k files.
import { characterDataMutationTags } from './bucket-wpt.mjs';

test('bucket-wpt: importing the module does NOT run the classifier', () => {
  // If the entry-point guard regressed, the import above would have started
  // a full corpus walk (and, without tools/wpt present, process.exit(1)).
  // Reaching this assertion at all is the pin; the symbol check makes the
  // intent explicit rather than relying on an empty test body.
  assert.equal(typeof characterDataMutationTags, 'function');
});

test('wave30 A3: an inline script writing .data earns requires-script-mutation', () => {
  // The exact script of css/selectors/dir-selector-auto-direction-change-001:
  // it flips a dir=auto subtree from Arabic to Latin, which is the ONLY
  // reason `:dir(ltr) + #target { background-color: green }` starts matching.
  const html = '<div dir="auto"><div id="inner">رسمية</div></div>'
    + '<script>\n  inner.offsetTop;\n  inner.firstChild.data = "LTR";\n</script>';
  assert.deepEqual(characterDataMutationTags(html), ['requires-script-mutation']);
});

test('wave30 A3: the other CharacterData write APIs are covered too', () => {
  // DOM §4.10 — the interface's whole mutating surface.
  for (const call of [
    'n.nodeValue = "x"',
    'n.replaceData(0, 1, "x")',
    'n.appendData("x")',
    'n.insertData(0, "x")',
    'n.deleteData(0, 1)',
  ]) {
    assert.deepEqual(characterDataMutationTags(`<script>${call}</script>`),
      ['requires-script-mutation'], call);
  }
});

test('wave30 A3: the supplement stays scoped and does not over-tag', () => {
  // No script at all.
  assert.deepEqual(characterDataMutationTags('<p>a.data = 1</p>'), []);
  // A script that reads rather than writes.
  assert.deepEqual(characterDataMutationTags('<script>const v = n.data;</script>'), []);
  // EXTERNAL scripts are Rule 4's documented exclusion (they go through the
  // bucket-C remote-resource rule) — the supplement must honour it.
  assert.deepEqual(
    characterDataMutationTags('<script src="x.js">n.data = "y"</script>'), []);
  // Defensive: a missing document is not a mutation.
  assert.deepEqual(characterDataMutationTags(undefined), []);
  assert.deepEqual(characterDataMutationTags(''), []);
});
