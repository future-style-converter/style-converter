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
  // Wave 52 (lane L6 T7): `<style>@counter-style c{}</style>` LEFT this list
  // — author rules are resolved now (see the T7 block below); a `<script>`
  // the CSSOM recogniser cannot read still bails, as the third entry pins.
  for (const html of ['<style>li{counter-increment:x}</style>', '<style>li{counter-set:x 3}</style>',
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

// ── WAVE 52, LANE L6 (T7): AUTHOR `@counter-style` RULES ────────────────────
// Every marker string below is the one the WPT reference paints (the
// `cssom-*-ref.html` sources list them as `<div>001.</div>` …), so a
// descriptor-semantics divergence from Blink fails loudly here first.
//
// MUTATION PROOF (EXECUTED 2026-09-25 at authoring, RE-EXECUTED 2026-10-05
// by tools/titan/results/wave52-counters-and-lists/mutate.py, entry
// `bake-inrange` in mutations.log, same four): making
// `inRange` answer true unconditionally turned FOUR tests red — the
// armenian §4 pin above (10000 → "ՔՌ." instead of "10000."), the §7.1.1
// negative pin (upper-roman -3 → "-III." instead of the decimal "-3."),
// the `fixed / range / additive … fall back` pin below and the corpus
// integration — and left everything else green; restored byte-exact
// (cmp-verified against the pre-mutation snapshot).

import fs from 'node:fs';
import { SCRIPT_MUTATION_REASON } from './counter-style-bake.mjs';

/** The three-item `<ol style="list-style-type: foo …">` shape every cssom
 *  test uses, as an extractor fixture; `start` optional. */
function authorListFixture(start) {
  const root = { _tag: 'ol', properties: { 'list-style-type': 'foo', 'list-style-position': 'inside' },
    children: { a: { _tag: 'li', properties: {} }, b: { _tag: 'li', properties: {} }, c: { _tag: 'li', properties: {} } } };
  if (start !== undefined) root._attrs = { start: String(start) };
  return { _wpt: { lossy: false, lossyReasons: [] }, components: { root } };
}
const markersOf = (fx) => Object.values(fx.components.root.children).map((c) => c._markerText);

test('T7: `extends decimal; pad: 3 "0"` stamps 001. 002. 003. (cssom-pad-setter-ref)', () => {
  const fx = authorListFixture();
  const out = bakeCounterStyles(fx, `<style id="sheet">@counter-style foo { system: extends decimal; pad: 3 '0'; }</style>`);
  assert.equal(out.status, 'baked');
  assert.deepEqual(markersOf(fx), ['001.', '002.', '003.']);
  // Resolved, not approximated: nothing lossy, the baked stamp is set.
  assert.equal(fx._wpt.lossy, false);
  assert.equal(fx._wpt.counterStyleBaked, true);
});

test('T7: `cyclic; symbols: A B C; prefix "("; suffix ")"` stamps (A) (B) (C)', () => {
  const fx = authorListFixture();
  bakeCounterStyles(fx, `<style>@counter-style foo { system: cyclic; symbols: A B C; prefix: '('; suffix: ')'; }</style>`);
  assert.deepEqual(markersOf(fx), ['(A)', '(B)', '(C)']);
});

test('T7: `negative "(" ")"` on extends decimal with start=-3 stamps (3). (2). (1).', () => {
  // §3.1.4: the negative PREFIX and SUFFIX wrap the representation; the
  // style's own `.` suffix (inherited from decimal) follows.
  const fx = authorListFixture(-3);
  bakeCounterStyles(fx, `<style>@counter-style foo { system: extends decimal; negative: '(' ')'; }</style>`);
  assert.deepEqual(markersOf(fx), ['(3).', '(2).', '(1).']);
});

test('T7: fixed / range / additive author systems fall back exactly like the refs', () => {
  // cssom-fallback-setter-ref: fixed A B, third item falls back to lower-roman.
  const fb = authorListFixture();
  bakeCounterStyles(fb, `<style>@counter-style foo { system: fixed; symbols: A B; fallback: lower-roman; }</style>`);
  assert.deepEqual(markersOf(fb), ['A.', 'B.', 'iii.']);
  // cssom-range-setter-ref: cyclic A B C with `range: 1 2` → 3 is decimal.
  const rg = authorListFixture();
  bakeCounterStyles(rg, `<style>@counter-style foo { system: cyclic; symbols: A B C; range: 1 2; }</style>`);
  assert.deepEqual(markersOf(rg), ['A.', 'B.', '3.']);
  // cssom-additive-symbols-setter-ref: `2 C, 1 B, 0 A` from start=0.
  const ad = authorListFixture(0);
  bakeCounterStyles(ad, `<style>@counter-style foo { system: additive; additive-symbols: 2 C, 1 B, 0 A; }</style>`);
  assert.deepEqual(markersOf(ad), ['A.', 'B.', 'C.']);
  // cssom-symbols-setter-ref: alphabetic A B C.
  const al = authorListFixture();
  bakeCounterStyles(al, `<style>@counter-style foo { system: alphabetic; symbols: A B C; }</style>`);
  assert.deepEqual(markersOf(al), ['A.', 'B.', 'C.']);
});

test('T7: a script of INVALID CSSOM setters is inert; a setter that takes effect bails', () => {
  // cssom-pad-setter-invalid's script, verbatim: every value the §7.1
  // grammar rejects — the static rule IS the rendering.
  const inert = `<style id="sheet">@counter-style foo { system: extends decimal; pad: 3 '0'; }</style>
<script>
document.body.offsetWidth;
const sheet = document.getElementById('sheet');
const foo_rule = sheet.sheet.rules[0];
foo_rule.pad = '-1 "0"';
foo_rule.pad = '3';
foo_rule.pad = '3 "X" "Y"';
</script>`;
  const fx = authorListFixture();
  assert.equal(bakeCounterStyles(fx, inert).status, 'baked');
  assert.deepEqual(markersOf(fx), ['001.', '002.', '003.']);
  // cssom-pad-setter (the VALID twin): `pad = '3 "0"'` on a rule without pad
  // changes the rendering after load → the extraction wall, named as such.
  const live = `<style id="sheet">@counter-style foo { system: extends decimal; }</style>
<script>
document.body.offsetWidth;
const sheet = document.getElementById('sheet');
const foo_rule = sheet.sheet.rules[0];
foo_rule.pad = '3 "0"';
</script>`;
  const fy = authorListFixture();
  const out = bakeCounterStyles(fy, live);
  assert.equal(out.status, 'bailed');
  assert.match(out.reason, /^requires-script-mutation/);
  assert.deepEqual(fy._wpt.lossyReasons, [COUNTER_STYLE_LOSSY_REASON, SCRIPT_MUTATION_REASON]);
  assert.equal(fy.components.root.children.a._markerText, undefined);
});

test('T7: an author rule extending a bullet is out of scope like the bullet', () => {
  // css-lists/content-property/marker-text-matches-disc: `my-disc { system:
  // extends disc }` + `ol { list-style: my-disc inside }` — no stamp, no
  // decline, not lossy (the natives keep their own disc).
  const fx = { _wpt: { lossy: false, lossyReasons: [] }, components: { root: {
    _tag: 'ol', properties: { 'list-style': 'my-disc inside' }, children: { a: { _tag: 'li', properties: {} } } } } };
  const out = bakeCounterStyles(fx, `<style>@counter-style my-disc { system: extends disc; }</style>`);
  assert.deepEqual([out.status, out.stamped, out.declined, fx._wpt.lossy], ['baked', 0, 0, false]);
});

test('T7: an INVALID author rule defines nothing — the name declines, never guesses', () => {
  // counter-style-at-rule/broken-symbols: `symbols: ⓐ inherit` — `inherit`
  // is a CSS-wide keyword, not a <custom-ident>, so the rule is invalid and
  // Blink paints "1." (the UA decimal for an undefined name). The bake
  // DECLINES (null) so the natives fall to their own UA default, exactly
  // the pre-T7 picture.
  const fx = authorListFixture();
  const out = bakeCounterStyles(fx, `<style>@counter-style foo { system: alphabetic; symbols: ⓐ inherit; }</style>`);
  assert.equal(out.declined, 3);
  assert.equal(fx.components.root.children.a._markerText, undefined);
});

test('T7: author names are case-sensitive, predefined keywords are not (§3)', () => {
  const author = { 'Custom-Style': { system: 'cyclic', symbols: ['‣'], ranges: null, fallback: 'decimal', prefix: '', suffix: '.' } };
  assert.equal(resolvedListStyleType({ 'list-style-type': 'Custom-Style' }, 'decimal', author), 'Custom-Style');
  // The lowercase spelling is a DIFFERENT (undefined) name → lowercased path
  // → not predefined → the bake declines it downstream.
  assert.equal(resolvedListStyleType({ 'list-style-type': 'custom-style' }, 'decimal', author), 'custom-style');
  assert.equal(markerStringFor('custom-style', 1, author), null);
  assert.equal(markerStringFor('Custom-Style', 1, author), '‣.');
});

test('T7 integration: every gate cssom -invalid fixture bakes the marker its ref paints', (t) => {
  // Reads the real extractor fixtures and authored sources when the
  // gitignored corpora are present (they are on the gate host); skips
  // honestly otherwise so CI without tools/wpt is not a false green.
  const expected = {
    'additive-symbols': ['A.', 'B.', 'C.'], fallback: ['A.', 'B.', 'iii.'],
    name: ['A.', 'B.', 'C.', 'X.', 'Y.', 'Z.'], negative: ['(3).', '(2).', '(1).'],
    pad: ['001.', '002.', '003.'], 'prefix-suffix': ['(A)', '(B)', '(C)'],
    range: ['A.', 'B.', '3.'], symbols: ['A.', 'B.', 'C.'],
  };
  const fxDir = 'fixtures/wpt/css-counter-styles', srcDir = 'tools/wpt/css/css-counter-styles/cssom';
  if (!fs.existsSync(fxDir) || !fs.existsSync(srcDir)) { t.skip('corpus not present'); return; }
  for (const [stem, want] of Object.entries(expected)) {
    const fx = JSON.parse(fs.readFileSync(`${fxDir}/cssom__cssom-${stem}-setter-invalid.json`, 'utf8'));
    const src = fs.readFileSync(`${srcDir}/cssom-${stem}-setter-invalid.html`, 'utf8');
    assert.equal(bakeCounterStyles(fx, src).status, 'baked', stem);
    const got = [];
    const walk = (n) => { if (n._markerText) got.push(n._markerText); for (const c of Object.values(n.children ?? {})) walk(c); };
    for (const r of Object.values(fx.components)) walk(r);
    assert.deepEqual(got, want, stem);
  }
});
