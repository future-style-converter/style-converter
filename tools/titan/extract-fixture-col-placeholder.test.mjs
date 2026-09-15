#!/usr/bin/env node
//
// Unit tests for NON_BOX_GENERATING_TAGS — the wave-50 lane B5 seam-S2 term
// on buildNode's 100x100 "empty node" placeholder in
// tools/titan/extract-fixture.mjs (css-display-3 §2.4: `col` / `colgroup`
// have an INTERNAL layout display and generate no box of their own, so
// forcing 100x100 on one does not add a placeholder — it PINS the table's
// column width, css-tables-3 §2.1).
//
// WHY ITS OWN FILE, AND WHY IT EXISTS AT ALL. Skeptic S2 found the seam
// shipped with NO pin: emptying the set left
// `node --test tools/titan/extract-fixture*.test.mjs` at 489/489 green,
// while every other wave-50 extractor seam fails under an equivalent
// one-line mutation (B1 4 · B4 2 · B7-hr 6 · B7-root 3 · B7-table 2). A
// check nobody can make fail is not a check. Own file for the same reason as
// extract-fixture-ua-hr.test.mjs / -root-scope / -table-text: the seam lands
// against a file several wave-50 lanes edit, and
// `node --test tools/titan/*.test.mjs` picks this up with no runner change.
//
// MUTATION PROOF (wave-50 fix lane F3, executed; copy → sha256 → edit → run
// → restore → sha256 re-verified):
//   * `NON_BOX_GENERATING_TAGS = new Set([])` — the three positive pins below
//     go red (the corpus `<col>`s and the rule-less `<colgroup>` regain the
//     100x100 stamp). This is the mutation that used to leave the suite
//     entirely green.
//   * `NON_BOX_GENERATING_TAGS = new Set(['col','colgroup','div','hr'])` —
//     the negative pin goes red. (Emptying the set cannot fail a negative
//     membership pin, so it gets its own mutation; a set that swallowed
//     `div` would silently delete the placeholder the branch exists for.)
//
// The markup is VERBATIM from the two gate-corpus tests that are the WHOLE
// static-path census of this stamp (8 of the 1435 gate tests carry a
// `<col>`/`<colgroup>` at all; exactly these two stamped one).

import { test } from 'node:test';
import assert from 'node:assert/strict';

import {
  parseCss, buildComponents, NON_BOX_GENERATING_TAGS,
} from './extract-fixture.mjs';

/** Flatten buildComponents' nested `children` maps into [id, component]. */
function flatten(components) {
  const out = [];
  const walk = (map) => {
    for (const [id, cmp] of Object.entries(map)) {
      out.push([id, cmp]);
      if (cmp.children) walk(cmp.children);
    }
  };
  walk(components);
  return out;
}

/** Every component carrying `_tag`, as a tag → [component] index. */
function byTag(components) {
  const idx = new Map();
  for (const [, cmp] of flatten(components)) {
    if (!cmp._tag) continue;
    if (!idx.has(cmp._tag)) idx.set(cmp._tag, []);
    idx.get(cmp._tag).push(cmp);
  }
  return idx;
}

test('a rule-less <col> is not stamped, while a rule-less <div> still is', () => {
  // css-writing-modes/direction-upright-002, VERBATIM — the stylesheet's two
  // table rules and one of its ten identical table blocks, plus the trailing
  // rule-less <div> that is the CONTROL. `col:last-child` claims the second
  // <col>; the FIRST matches no rule, carries no text and has no children,
  // so it is exactly the shape the placeholder branch would otherwise claim.
  const css = parseCss(`
  colgroup:first-child { background: fuchsia; }
  col:last-child { background: purple; }`);
  const html = '<body><div><table>\n'
    + '    <colgroup></colgroup>\n'
    + '    <colgroup><col><col></colgroup>\n'
    + '    <tr><td>A<td>B<td>C\n'
    + '  </table></div><div></div></body>';
  const { components } = buildComponents(html, css, 'du2');
  const cols = byTag(components).get('col');
  assert.equal(cols.length, 2);
  // THE PIN: no invented geometry on the rule-less column box.
  assert.equal(cols[0].properties.width, undefined);
  assert.equal(cols[0].properties.height, undefined);
  assert.deepEqual(cols[0].properties, {});
  // The matched sibling is untouched by the term — it never reached the
  // placeholder branch, and its author declaration still ships.
  assert.deepEqual(cols[1].properties, { background: 'purple' });
  // THE CONTROL, in the same document: a rule-less, text-less, child-less
  // <div> is what the placeholder was built for and must still get it.
  const plain = flatten(components)
    .map(([, c]) => c)
    .filter((c) => !c._tag && c.properties.width === '100px');
  assert.equal(plain.length, 1, 'the bare <div> must still be stamped 100x100');
  assert.equal(plain[0].properties.height, '100px');
});

test('the three <col>s of border-collapse-dynamic-col-001 ship no geometry', () => {
  // css-tables/border-collapse-dynamic-col-001, VERBATIM — the test S2 found
  // the original census missed, and the reason this pin matters: its
  // wave49-final cells are web P 1.0000 · iOS f 0.9404 · Android P 0.9812, so
  // TWO PASSING CELLS ride on this. (The census that said "exactly one test"
  // was taken over wave49-final's per-test IR, where this test ran
  // `[post-load: extracted+structure]` and the stamp is invisible; post-load
  // bailed 49x and declined 9x in that same run, so bail-to-static is live.)
  // Its <style> declares nothing for `col`, so all three are rule-less.
  const css = parseCss(`
  table {
    border-collapse: collapse;
    border-spacing: 0;
  }
  td {
    padding: 10px;
    border: 1px solid;
  }`);
  const html = '<body><table>\n'
    + '  <colgroup>\n    <col>\n    <col>\n    <col>\n  </colgroup>\n'
    + '  <tbody>\n    <tr>\n      <td></td>\n      <td></td>\n      <td></td>\n'
    + '    </tr>\n  </tbody>\n</table></body>';
  const { components } = buildComponents(html, css, 'border-collapse-dynamic-col-001');
  const cols = byTag(components).get('col');
  assert.equal(cols.length, 3);
  for (const col of cols) {
    assert.deepEqual(col.properties, {},
      'a rule-less <col> pins the column width if it is given one');
  }
});

test('a rule-less <colgroup> is not stamped either', () => {
  // The colgroup half of the set, exercised on direction-upright-002's own
  // `<colgroup></colgroup>` — an EMPTY column group, so the `!node.children`
  // guard that protects rule-less wrappers cannot reach it and only this
  // term can. The document's `colgroup:first-child` rule is deliberately NOT
  // included here: with it the element matches a rule and never reaches the
  // placeholder branch at all, which is precisely why the corpus has no
  // rule-less carrier for this half today and why it needs a pin of its own
  // rather than a census. A column GROUP generates no box either
  // (css-display-3 §2.4 `table-column-group`), so the same reasoning holds.
  const { components } = buildComponents(
    '<body><table><colgroup></colgroup><tr><td>A</table><div></div></body>',
    parseCss('col:last-child { background: purple; }'),
    'cg');
  const groups = byTag(components).get('colgroup');
  assert.equal(groups.length, 1);
  assert.deepEqual(groups[0].properties, {});
  // Same-document control again — the placeholder itself is still alive.
  const plain = flatten(components)
    .map(([, c]) => c)
    .filter((c) => !c._tag && c.properties.width === '100px');
  assert.equal(plain.length, 1);
});

test('NEGATIVE: <hr> and <div> are NOT non-box-generating', () => {
  // Membership, asserted directly, because behaviour can no longer see it:
  // BACKLOG queue 5(a)'s UA `<hr>` bake claims the tag BEFORE the placeholder
  // branch is reached, so an `<hr>` would come out un-stamped whether or not
  // it were in this set. Both tags DO generate boxes — `<hr>` is a replaced
  // separator the browser paints as a 2px rule (HTML Rendering §15.3.11) and
  // `<div>` is the block wrapper the 100x100 placeholder exists for — so
  // admitting either would delete real geometry rather than stop inventing
  // it. This pin is what makes that a decision instead of an accident.
  assert.equal(NON_BOX_GENERATING_TAGS.has('hr'), false);
  assert.equal(NON_BOX_GENERATING_TAGS.has('div'), false);
  // …and the set is exactly the two internal-display tags, nothing else has
  // been swept in alongside them.
  assert.deepEqual([...NON_BOX_GENERATING_TAGS].sort(), ['col', 'colgroup']);
});
