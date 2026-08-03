// tools/titan/counter-style-bake.test.mjs — wave-27 lane CBAKE.
//
// Pins the fifth bake against its two authorities:
//   1. css-counter-styles-3 itself (§2 systems, §4 range, §7.1.4 fallback,
//      §3.1.5 suffix) — the algorithm tests;
//   2. the LIVE WPT corpus expectations — every marker string asserted
//      below is copied out of the authored test markup under
//      tools/wpt/css/css-counter-styles/ (each `<li title='N'>X</li>` says
//      "counter value N renders as X"), so a table typo cannot pass.

import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  markerStringFor, counterRepresentation, numericRepresentation,
  alphabeticRepresentation, additiveRepresentation, resolvedListStyleType,
  ordinalFor, bakeCounterStyles, fixtureHasList, COUNTER_STYLE_LOSSY_REASON,
} from './counter-style-bake.mjs';
import { PREDEFINED, BULLET_STYLES } from './counter-style-table.mjs';

// ── §2 system algorithms ────────────────────────────────────────────────────

test('numeric system is positional with a real zero digit (§2.3)', () => {
  const d = PREDEFINED.decimal.symbols;
  assert.equal(numericRepresentation(0, d), '0');
  assert.equal(numericRepresentation(1005, d), '1005');
});

test('alphabetic system is BIJECTIVE base-N, not a cyclic index (§2.4)', () => {
  const a = PREDEFINED['lower-alpha'].symbols;
  // 26 → "z" (not "ba"), 27 → "aa": the defining bijective boundary.
  assert.equal(alphabeticRepresentation(26, a), 'z');
  assert.equal(alphabeticRepresentation(27, a), 'aa');
});

test('additive system returns null when the table cannot spell a value (§2.6)', () => {
  // A table with only a weight-3 symbol cannot spell 4.
  assert.equal(additiveRepresentation(4, [[3, 'x']]), null);
  assert.equal(additiveRepresentation(6, [[3, 'x']]), 'xx');
});

// ── §4 range + §7.1.4 fallback ──────────────────────────────────────────────

test('armenian spells its whole range and falls back to decimal past it', () => {
  // css3-counter-styles-008 asserts exactly this boundary.
  assert.equal(markerStringFor('armenian', 9999), 'ՔՋՂԹ.');
  assert.equal(markerStringFor('armenian', 10000), '10000.');
  assert.equal(markerStringFor('armenian', 10001), '10001.');
});

test('the fallback supplies the REPRESENTATION, the original style the suffix', () => {
  // cjk-decimal's suffix is U+3001; out of range it would keep that comma.
  // Here the check is the in-range half plus decimal's own full stop.
  assert.equal(markerStringFor('cjk-decimal', 10), '一〇、');
  assert.equal(markerStringFor('decimal', 10), '10.');
});

test('an unmodelled counter style resolves to null, never to decimal', () => {
  // §7.1's "undefined name behaves as decimal" is deliberately NOT applied:
  // silently substituting decimal would overwrite whatever the native's own
  // table knows. null ⇒ the bake declines to stamp.
  assert.equal(counterRepresentation('japanese-formal', 1), null);
  assert.equal(markerStringFor('simp-chinese-informal', 1), null);
});

// ── the live WPT corpus expectations ────────────────────────────────────────

test('WPT arabic-indic / bengali / cambodian markers reproduce exactly', () => {
  // Values + expected glyphs lifted from css3-counter-styles-101/117/159.
  assert.equal(markerStringFor('arabic-indic', 1), '١.');
  assert.equal(markerStringFor('arabic-indic', 9), '٩.');
  assert.equal(markerStringFor('bengali', 1065), '১০৬৫.');
  assert.equal(markerStringFor('bengali', 100), '১০০.');
  assert.equal(markerStringFor('cambodian', 1860), '១៨៦០.');
  assert.equal(markerStringFor('cambodian', 11), '១១.');
});

test('WPT armenian markers reproduce exactly (css3-counter-styles-007)', () => {
  for (const [value, glyphs] of [[10, 'Ժ'], [11, 'ԺԱ'], [43, 'ԽԳ'], [80, 'Ձ'],
    [99, 'ՂԹ'], [101, 'ՃԱ'], [222, 'ՄԻԲ'], [540, 'ՇԽ'], [999, 'ՋՂԹ'],
    [1000, 'Ռ'], [1065, 'ՌԿԵ'], [1800, 'ՌՊ']]) {
    assert.equal(markerStringFor('armenian', value), `${glyphs}.`);
  }
});

test('decimal-leading-zero pads to two digits and stops (§3.1.6 pad)', () => {
  assert.equal(markerStringFor('decimal-leading-zero', 7), '07.');
  assert.equal(markerStringFor('decimal-leading-zero', 107), '107.');
});

test('suffixes carry no trailing space — the natives own that gap', () => {
  // The single deliberate deviation from a literal §3.1.5 transcription;
  // pinned so a "fix" that re-adds the space fails loudly here first.
  for (const style of Object.values(PREDEFINED)) {
    assert.equal(style.suffix, style.suffix.trimEnd());
  }
});

// ── cascade + ordinal resolution ────────────────────────────────────────────

test('list-style-type resolution: own longhand > shorthand token > inherited', () => {
  assert.equal(resolvedListStyleType({ 'list-style-type': 'Bengali' }, 'decimal'), 'bengali');
  assert.equal(resolvedListStyleType({ 'list-style': 'inside upper-roman' }, 'decimal'), 'upper-roman');
  assert.equal(resolvedListStyleType({}, 'armenian'), 'armenian');
});

test('ordinalFor honours <li value> and otherwise continues the run (HTML §4.4.8)', () => {
  assert.equal(ordinalFor({ value: '4' }, 99), 4);
  assert.equal(ordinalFor(undefined, 7), 7);
  assert.equal(ordinalFor({ value: 'not-a-number' }, 7), 7);
});

// ── the fixture pass ────────────────────────────────────────────────────────

/** One `<ol>` with `start` and `n` items, in extractor fixture shape. */
function listFixture(start, type, count, extra = {}) {
  const children = {};
  for (let i = 0; i < count; i++) {
    children[`li${i}`] = { _tag: 'li', properties: { 'list-style-type': type }, ...(extra[i] ?? {}) };
  }
  return {
    _wpt: { lossy: false, lossyReasons: [] },
    components: { root: { _tag: 'ol', properties: {}, _attrs: { start: String(start) }, children } },
  };
}

test('a fixture with no list is left byte-identical', () => {
  const fx = { _wpt: { lossy: false, lossyReasons: [] }, components: { a: { properties: {} } } };
  const before = JSON.stringify(fx);
  assert.equal(fixtureHasList(fx), false);
  assert.deepEqual(bakeCounterStyles(fx, ''), { status: 'skipped', stamped: 0, declined: 0 });
  assert.equal(JSON.stringify(fx), before);
});

test('<ol start> seeds the ordinal and each item advances it', () => {
  const fx = listFixture(1860, 'cambodian', 3);
  const out = bakeCounterStyles(fx, '');
  assert.equal(out.status, 'baked');
  assert.equal(out.stamped, 3);
  const items = Object.values(fx.components.root.children);
  assert.deepEqual(items.map((c) => c._markerText), ['១៨៦០.', '១៨៦១.', '១៨៦២.']);
  assert.equal(fx._wpt.counterStyleBaked, true);
  // Nothing declined ⇒ nothing lossy: the string is the spec's own answer.
  assert.equal(fx._wpt.lossy, false);
});

test('<li value> resets the run for itself AND its successors', () => {
  const fx = listFixture(1, 'decimal', 3, { 1: { _attrs: { value: '10' } } });
  bakeCounterStyles(fx, '');
  assert.deepEqual(
    Object.values(fx.components.root.children).map((c) => c._markerText),
    ['1.', '10.', '11.'],
  );
});

test('§6.1 bullets are out of scope by design — no stamp, no lossy flag', () => {
  for (const bullet of BULLET_STYLES) {
    const fx = listFixture(1, bullet, 2);
    const out = bakeCounterStyles(fx, '');
    assert.equal(out.stamped, 0, bullet);
    assert.equal(out.declined, 0, bullet);
    assert.equal(fx._wpt.lossy, false, bullet);
  }
});

test('an unmodelled counter style declines LOUDLY instead of guessing', () => {
  const fx = listFixture(1, 'japanese-formal', 2);
  const out = bakeCounterStyles(fx, '');
  assert.equal(out.stamped, 0);
  assert.equal(out.declined, 2);
  assert.equal(fx._wpt.lossy, true);
  assert.deepEqual(fx._wpt.lossyReasons, [COUNTER_STYLE_LOSSY_REASON]);
  assert.equal(fx.components.root.children.li0._markerText, undefined);
});

test('a list-style-image marker is skipped without a decline', () => {
  const fx = listFixture(1, 'decimal', 1,
    { 0: { properties: { 'list-style-type': 'decimal', 'list-style-image': 'url(a.png)' } } });
  const out = bakeCounterStyles(fx, '');
  assert.equal(out.stamped, 0);
  assert.equal(out.declined, 0);
});

test('dynamic counters bail the WHOLE fixture, stamping nothing', () => {
  for (const html of ['<style>li{counter-increment:x}</style>', '<style>@counter-style c{}</style>',
    '<p>x<script>go()</script>', '<style>li::before{content:counters(x,".")}</style>']) {
    const fx = listFixture(1, 'decimal', 2);
    const out = bakeCounterStyles(fx, html);
    assert.equal(out.status, 'bailed', html);
    assert.equal(fx.components.root.children.li0._markerText, undefined, html);
    assert.deepEqual(fx._wpt.lossyReasons, [COUNTER_STYLE_LOSSY_REASON], html);
  }
});

test('list-style-type inherited from the container reaches the items', () => {
  const fx = {
    _wpt: { lossy: false, lossyReasons: [] },
    components: {
      root: {
        _tag: 'ol', properties: { 'list-style-type': 'upper-roman' },
        children: { a: { _tag: 'li', properties: {} }, b: { _tag: 'li', properties: {} } },
      },
    },
  };
  bakeCounterStyles(fx, '');
  assert.deepEqual(Object.values(fx.components.root.children).map((c) => c._markerText),
    ['I.', 'II.']);
});

// ── ADVERSARIAL REGRESSIONS ─────────────────────────────────────────────────
// Every expectation below was MEASURED against Blink (puppeteer, marker text
// read off the accessibility tree) on the live corpus test named in the
// comment, and every one of them was WRONG before the fix.

test('§7.1.1 negative values carry the `negative` prefix, not an empty string', () => {
  // Blink: `<ol start="-1">` paints "-1." "0." "1." "2.";
  //        `<ol start="-3" style="list-style-type:upper-roman">` paints
  //        "-3." "-2." "-1." (out of roman's range ⇒ decimal + sign).
  assert.equal(markerStringFor('decimal', -1), '-1.');
  assert.equal(markerStringFor('arabic-indic', -2), '-٢.');
  assert.equal(markerStringFor('upper-roman', -3), '-3.');
  assert.equal(markerStringFor('armenian', -3), '-3.');
  assert.equal(markerStringFor('lower-alpha', -2), '-2.');
  // §3.1.6 pad counts the sign: Blink paints decimal-leading-zero -1 as
  // "-1." and 0 as "00.".
  assert.equal(markerStringFor('decimal-leading-zero', -1), '-1.');
  assert.equal(markerStringFor('decimal-leading-zero', 0), '00.');
  // cjk-decimal's §6.2 range is `0 infinity`, so a negative falls back to
  // ASCII decimal but keeps its own U+3001 suffix — Blink paints "-2、".
  assert.equal(markerStringFor('cjk-decimal', -2), '-2、');
});

test('a `list-style` shorthand naming an unmodelled type DECLINES', () => {
  // css-lists/list-style-type-string-001b: `ol, ul { list-style: inside "# " }`
  // used to resolve to the UA `decimal` and stamp "." / "0." / "1." / "2."
  // over a marker Blink paints as "#".
  assert.equal(markerStringFor(resolvedListStyleType({ 'list-style': 'inside "# "' }, 'decimal'), 1), null);
  assert.equal(markerStringFor(resolvedListStyleType({ 'list-style': 'korean-hangul-formal' }, 'decimal'), 1), null);
  // No type token at all ⇒ the shorthand RESETS the type to its initial
  // `disc` (Blink paints a bullet for both) — never the inherited value.
  assert.equal(resolvedListStyleType({ 'list-style': 'inside' }, 'decimal'), 'disc');
  assert.equal(resolvedListStyleType({ 'list-style': 'url(a.png)' }, 'decimal'), 'disc');
  // …and a recognised token still wins, unchanged.
  assert.equal(resolvedListStyleType({ 'list-style': 'inside upper-roman' }, 'decimal'), 'upper-roman');
});

test('an <li> whose display is not list-item paints no marker', () => {
  // css-images/gradient/gradient-powerless-hue-hsl: `.test { display: flex }`
  // on every item ⇒ Blink shows ZERO markers; the bake used to stamp 1.–4.
  const fx = listFixture(1, 'decimal', 3,
    { 1: { properties: { 'list-style-type': 'decimal', display: 'flex' } } });
  const out = bakeCounterStyles(fx, '');
  assert.equal(out.stamped, 2);
  const items = Object.values(fx.components.root.children);
  assert.equal(items[1]._markerText, undefined);
  // A skipped item does NOT increment the list-item counter (css-lists-3 §4.1).
  assert.deepEqual([items[0]._markerText, items[2]._markerText], ['1.', '2.']);
  // The two-value `block flow list-item` spelling still generates a marker.
  assert.equal(bakeCounterStyles(
    listFixture(1, 'decimal', 1,
      { 0: { properties: { 'list-style-type': 'decimal', display: 'block flow list-item' } } }), '').stamped, 1);
});

test('::marker content and <ol reversed> BAIL instead of stamping a wrong ordinal', () => {
  for (const html of [
    '<style>li::marker { content: "X" }</style>',   // css-pseudo/marker-content-003 → Blink "X X X"
    '<style>::marker { content: none }</style>',    // marker-content-019 → Blink paints nothing
    '<ol reversed><li>a<li>b</ol>',                 // li-value-reversed-011 → Blink "3. 2. 1."
    '<ol start=10 reversed>',                       // contain-style-ol-ordinal-start-reversed
  ]) {
    const fx = listFixture(1, 'decimal', 2);
    const out = bakeCounterStyles(fx, html);
    assert.equal(out.status, 'bailed', html);
    assert.equal(fx.components.root.children.li0._markerText, undefined, html);
    assert.deepEqual(fx._wpt.lossyReasons, [COUNTER_STYLE_LOSSY_REASON], html);
  }
  // A plain forward <ol> is untouched by the gate.
  assert.equal(bakeCounterStyles(listFixture(1, 'decimal', 2), '<ol start=3><li>a</ol>').status, 'baked');
});

test('a document with non-<li> list items BAILS (their ordinals shift every marker)', () => {
  // css-lists/list-item-definition paints "2." … "8."; the <li>-only walker
  // stamped "1." … "4." because the `display:list-item` boxes ahead of them
  // increment the same counter and were never counted.
  for (const html of ['<style>div { display: list-item }</style>',
    '<style>li { display: inline list-item }</style>']) {
    const fx = listFixture(1, 'decimal', 2);
    assert.equal(bakeCounterStyles(fx, html).status, 'bailed', html);
    assert.equal(fx.components.root.children.li0._markerText, undefined, html);
  }
  // `display: flex` (or any non-list-item display) is NOT a bail — it is the
  // per-item skip pinned above.
  assert.equal(bakeCounterStyles(listFixture(1, 'decimal', 2),
    '<style>li { display: flex }</style>').status, 'baked');
});

test('a BAIL leaves a fixture with no _wpt block byte-identical', () => {
  // markLossy used to run `fixture._wpt ??= {}` BEFORE its guard, so a bail
  // handed such a fixture a brand-new empty `_wpt: {}`.
  const fx = { components: listFixture(1, 'decimal', 2).components };
  const before = JSON.stringify(fx);
  assert.equal(bakeCounterStyles(fx, '<script>x</script>').status, 'bailed');
  assert.equal(JSON.stringify(fx), before);
});

test('baking twice is idempotent', () => {
  const fx = listFixture(11, 'cambodian', 3);
  bakeCounterStyles(fx, '');
  const once = JSON.stringify(fx);
  bakeCounterStyles(fx, '');
  assert.equal(JSON.stringify(fx), once);
});

test('a <ul> without any declaration keeps its UA disc — nothing baked', () => {
  const fx = {
    _wpt: { lossy: false, lossyReasons: [] },
    components: { root: { _tag: 'ul', properties: {}, children: { a: { _tag: 'li', properties: {} } } } },
  };
  const out = bakeCounterStyles(fx, '');
  assert.equal(out.stamped, 0);
  assert.equal(out.declined, 0);
});
