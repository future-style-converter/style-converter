#!/usr/bin/env node
//
// Unit tests for the wave-50 lane B7 duplicated-table-text drop in
// tools/titan/extract-fixture.mjs (BACKLOG queue 5(g) and item 1(b);
// CSS 2.1 §17.2.1 "Anonymous table objects").
//
// Own file, same reason as the other two B7 suites: the change lands as a
// self-contained patch against a file another lane is editing in the same
// wave, and `node --test tools/titan/*.test.mjs` picks it up unchanged.
//
// The markup below is VERBATIM from the gate corpus — the five documents
// that are the complete census of ownText on a table-internal box over the
// 1435 wave49-final scored tests.

import { test } from 'node:test';
import assert from 'node:assert/strict';

import {
  isDuplicatedTableText, parseCss, buildComponents,
} from './extract-fixture.mjs';

/** Minimal walker-node shape the predicate reads. */
const node = (tag, ownText, kids = []) =>
  ({ tag, ownText, children: kids.map((t) => ({ ownText: t })) });

test('isDuplicatedTableText: only table-internal boxes', () => {
  // A <div> whose kept children render inside it is the case the ownText
  // hoist was BUILT for — it must never be touched.
  assert.equal(isDuplicatedTableText(node('div', 'ABC', ['A', 'B', 'C'])), false);
  // …and a cell is ordinary flow content too (CSS 2.1 §16.6).
  assert.equal(isDuplicatedTableText(node('td', 'ABC', ['A', 'B', 'C'])), false);
  assert.equal(isDuplicatedTableText(node('th', 'ABC', ['A', 'B', 'C'])), false);
  assert.equal(isDuplicatedTableText(node('caption', 'ABC', ['A', 'B', 'C'])), false);
  for (const tag of ['table', 'thead', 'tbody', 'tfoot', 'tr', 'colgroup', 'col']) {
    assert.equal(isDuplicatedTableText(node(tag, 'ABC', ['A', 'B', 'C'])), true, tag);
  }
});

test('isDuplicatedTableText: equality is the proof, whitespace aside', () => {
  // background-color-animation-with-table1's <table>: "1 2" over cells "1","2".
  assert.equal(isDuplicatedTableText(node('table', '1 2', ['1', '2'])), true);
  // contain-content-004's <table>: three U+00A0 with an ASCII space between.
  assert.equal(isDuplicatedTableText(
    node('table', '    ', ['  ', ' '])), true);
  // STRAY text in a row is NOT a duplicate — §17.2.1 really does wrap it in
  // an anonymous cell, so it must survive.
  assert.equal(isDuplicatedTableText(node('tr', 'stray1', ['1'])), false);
  assert.equal(isDuplicatedTableText(node('tr', '12', ['1'])), false);
  // Nothing to duplicate.
  assert.equal(isDuplicatedTableText(node('tr', '', ['1'])), false);
  assert.equal(isDuplicatedTableText(node('tr', '   ', ['1'])), false);
  assert.equal(isDuplicatedTableText(null), false);
});

test('isDuplicatedTableText: U+00A0 is content, not whitespace', () => {
  // String.trim() would call a lone &nbsp; empty and delete a box the
  // browser paints; css-text-3 §1.1 white space is space/tab/CR/LF/FF only.
  assert.equal(isDuplicatedTableText(node('tr', ' ', [])), false);
});

test('buildComponents: the row carries the cells once, not twice', () => {
  // background-color-animation-with-table1, VERBATIM markup. Before the drop
  // each <tr> shipped `_text: "1"` AND a `{text:"1"}` run beside the <td>
  // that already renders it — an anonymous phantom cell per row. The web and
  // iOS captures paint that duplicate (ink out to x 132 / x 38 against the
  // ref's x 26); Android already suppressed it and passes at 0.9998.
  const html = '<body><table>\n  <tr><td>1\n  <tr><td>2\n</table></body>';
  const { components } = buildComponents(html, parseCss('table { width: 160px }'), 'tbl1');
  const table = Object.values(components).find((c) => c._tag === 'table');
  assert.ok(table, 'the <table> must survive as its own component');
  assert.equal(table._text, undefined, 'the table must not repeat its cells');
  const rows = Object.values(table.children ?? {}).filter((c) => c._tag === 'tr');
  assert.equal(rows.length, 2);
  const cellTexts = [];
  for (const row of rows) {
    assert.equal(row._text, undefined, 'the row must not repeat its cells');
    for (const entry of row._runs ?? []) {
      assert.equal(entry.text, undefined,
        `run list must carry children only, got ${JSON.stringify(entry)}`);
    }
    for (const cell of Object.values(row.children ?? {})) cellTexts.push(cell._text);
  }
  // The content itself is untouched — dropped ONCE, not lost.
  assert.deepEqual(cellTexts, ['1', '2']);
});

test('buildComponents: a cell keeps its own text', () => {
  // The guard is scoped to table-internal boxes; a <td>'s own text is
  // ordinary flow content and must still reach the wire.
  const html = '<body><table>\n  <tr><td>&nbsp;<td>&nbsp;\n</table></body>';
  const { components } = buildComponents(html, parseCss('td { color: red }'), 'ws');
  const table = Object.values(components).find((c) => c._tag === 'table');
  const row = Object.values(table.children ?? {})[0];
  const cells = Object.values(row.children ?? {});
  assert.equal(cells.length, 2);
  for (const cell of cells) {
    assert.equal(cell._tag, 'td');
    assert.equal(cell._text, ' ');
  }
});
