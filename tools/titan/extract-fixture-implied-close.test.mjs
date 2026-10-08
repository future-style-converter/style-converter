//
// tools/titan/extract-fixture-implied-close.test.mjs — wave-53 lane L1
// (nested-list-extractor A): pins for findImpliedClose, the `li` implied-close
// SCOPE that tools/titan/results/wave53-lists-bakes/seam-1.patch adds to
// extract-fixture.mjs (walkChildren + scanOwnText trigger sites).
//
// HTML §13.2.6.4.7 ("in body", start tag `li`): the implied close stops at a
// special element other than address/div/p, so an `<li>` opener INSIDE a
// nested `<ol>` closes the inner item, never the outer one. Before the seam
// both scans took the FIRST `<li\b` after the open tag (MEASURED on
// wave52-ship css-lists/counter-reset-reversed-nested: a flat list, numbers
// 12/11/10/9/8, `1. One` dropped — tools/titan/results/wave53-plan/
// nested-list-extractor.md §2-§4).
//
// MUTATIONS (executed, logged in tools/titan/results/wave53-lists-bakes/
// _note.md): N1 goes red when findImpliedClose returns the first match
// (depth ignored); N2 goes red when only the scanOwnText site is reverted.

import test from 'node:test';
import assert from 'node:assert/strict';
import { existsSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

// Namespace import: a missing export reads `undefined` and fails the pin
// below loudly instead of failing the whole file at link time.
import * as EF from './extract-fixture.mjs';

// The real-corpus pin needs tools/wpt (gitignored) and the bucket index —
// skip-guarded exactly like extract-fixture.test.mjs's corpus pins so the
// hermetic CI run stays green on a checkout without the corpus.
const REPO_ROOT = join(dirname(fileURLToPath(import.meta.url)), '..', '..');
const WPT_DIR = process.env.WPT_DIR ?? join(REPO_ROOT, 'tools', 'wpt');
const NESTED = 'css/css-lists/counter-reset-reversed-nested.html';
const corpusReady = existsSync(join(WPT_DIR, NESTED))
  && existsSync(join(REPO_ROOT, 'tools', 'titan', 'wpt-buckets.json'));

// ── the helper itself ───────────────────────────────────────────────────────

test('findImpliedClose: exported, and the old first match when no list nests', () => {
  // The seam's one new export — absent means seam-1.patch is not applied.
  assert.equal(typeof EF.findImpliedClose, 'function');
  const li = new Set(['li']);
  // Identity by construction: no scope boundary opens before the trigger,
  // so the answer is the first `<li` opener (index 7 here), as before.
  assert.equal(EF.findImpliedClose('<li>foo<li>bar', 4, 'li', li), 7);
  // No trigger at all → -1, the "no implicit close" answer.
  assert.equal(EF.findImpliedClose('<li>foo</li>', 4, 'li', li), -1);
  // A tag with no scope entry (`p`) keeps the first match even when a list
  // opens first: the KNOWN-GAP side of the helper's banner.
  const p = new Set(['p', 'ol']);
  assert.equal(EF.findImpliedClose('<p>a<ol><li>b</ol>', 3, 'p', p), 4);
});

test('findImpliedClose: an `<li>` inside a nested list is skipped (HTML §13.2.6.4.7)', () => {
  const li = new Set(['li']);
  // `<li>Two <ol><li>Eleven</li></ol></li><li>One` — the nested opener at
  // depth 1 is skipped; the depth-0 `<li>One` after `</ol>` is the answer.
  const h = '<li>Two <ol><li>Eleven</li></ol></li><li>One';
  assert.equal(EF.findImpliedClose(h, 4, 'li', li), h.indexOf('<li>One'));
  // Every list container bounds the scope: ul, menu and dir as well.
  for (const tag of ['ul', 'menu', 'dir']) {
    const s = `<li>A<${tag}><li>B</${tag}><li>C`;
    assert.equal(EF.findImpliedClose(s, 4, 'li', li), s.indexOf('<li>C'), tag);
  }
  // An unclosed nested list never returns to depth 0 → no implied close.
  assert.equal(EF.findImpliedClose('<li>A<ol><li>B<li>C', 4, 'li', li), -1);
});

// ── N2: the run-proto agreement (scanOwnText site) ──────────────────────────

test('N2: the run proto of `<li>A<ol><li>B</li></ol></li>tail` has ONE element entry', () => {
  const ctx = { styledTags: new Set() };
  const r = EF.extractOwnTextMerged('<li>A<ol><li>B</li></ol></li>tail', ctx);
  // The parent owns only "tail"; the nested `<li>B` belongs to the inner
  // list, so the outer scan must see exactly one kept child (the outer li).
  assert.equal(r.text, 'tail');
  assert.deepEqual(r.runProto, [{ el: 0, tag: 'li' }, { text: 'tail' }]);
  // Control: the flat sibling shape counter-suffix carries is unchanged.
  assert.equal(EF.extractOwnTextMerged('<li>foo<li>bar', ctx).text, '');
});

// ── N1: the target document, end to end (walkChildren site + counter bake) ──

/** Depth-first `[tag, before._text, own _text]` rows of a component map. */
function rows(map, depth = 0, out = []) {
  for (const c of Object.values(map ?? {})) {
    // Only element components carry `_tag`; the bake's synthetic nodes do not.
    out.push({ depth, tag: c._tag ?? null, marker: c._pseudo?.before?._text ?? null,
      text: typeof c._text === 'string' ? c._text.trim() : null });
    rows(c.children, depth + 1, out);
  }
  return out;
}

test('N1: counter-reset-reversed-nested extracts NESTED with 3. 2. 11. 9. 8. 1.', { skip: !corpusReady }, async () => {
  const { fixture } = await EF.extractFixture(NESTED);
  const lis = rows(fixture.components).filter((r) => r.tag === 'li');
  // Six items in document order: Three, Two, then the nested three, then One.
  assert.deepEqual(lis.map((r) => r.text), ['Three', 'Two', 'Eleven', 'Nine', 'Eight', 'One']);
  // Eleven/Nine/Eight sit ONE level deeper than Three/Two/One (Two's <ol>).
  const [three, two, eleven, nine, eight, one] = lis;
  assert.equal(three.depth, two.depth);
  assert.equal(one.depth, two.depth);
  for (const n of [eleven, nine, eight]) assert.equal(n.depth, two.depth + 2);
  // The reference's numbers (counter-reset-reversed-nested-ref.html): the
  // structure (A) and the css-lists-3 §4.4.2 step-4 term (B) together.
  assert.deepEqual(lis.map((r) => r.marker), ['3. ', '2. ', '11. ', '9. ', '8. ', '1. ']);
  // Two holds exactly one nested <ol> child.
  const twoCmp = Object.values(Object.values(fixture.components)
    .find((c) => c._tag === 'ol').children)[1];
  const kids = Object.values(twoCmp.children ?? {});
  assert.deepEqual(kids.map((k) => k._tag), ['ol']);
});
