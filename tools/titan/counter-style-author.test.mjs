// tools/titan/counter-style-author.test.mjs — wave 52, lane L6 (T7).
//
// Pins for the two modules behind the bake's author-rule support: the
// css-counter-styles-3 §3 descriptor grammar (counter-style-descriptors.mjs)
// and the rule extraction / table / §7.1 CSSOM setter verdict
// (counter-style-author.mjs). Every "invalid" value is VERBATIM from a
// cssom-*-setter-invalid.html script, every "valid" one from its twin.
//
// MUTATION PROOF (EXECUTED 2026-09-25 at authoring; the pad half RE-EXECUTED
// 2026-10-05 by tools/titan/results/wave52-counters-and-lists/mutate.py,
// entry `author-pad` in mutations.log — 5 red: the three below plus the
// bake's inert-script and corpus-integration pins): `parsePad` accepting a
// negative length turned `every cssom -invalid assignment is rejected`
// (`-1 "0"`), the last-valid-value pin and the verdict pin red; making
// `setterTakesEffect` ignore the algorithm-change rule turned the
// `setterTakesEffect` and verdict pins red. Both restored byte-exact.

import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  tokenize, parseSymbol, parseName, parseSystem, parseNegative, parseAffix, parseRange,
  parsePad, parseFallback, parseSymbols, parseAdditiveSymbols, setterTakesEffect,
} from './counter-style-descriptors.mjs';
import {
  extractCounterStyleRules, buildAuthorTable, cssomScriptVerdict,
  cyclicRepresentation, fixedRepresentation, symbolicRepresentation,
} from './counter-style-author.mjs';

// ── tokenizer ───────────────────────────────────────────────────────────────

test('tokenize: strings, idents (with escapes and non-ASCII), integers, commas, functions', () => {
  assert.deepEqual(tokenize(`'(' ")" A -1 , \\3001 ⓐ url(a.png) *`).map((t) => [t.t, t.v]), [
    ['string', '('], ['string', ')'], ['ident', 'A'], ['int', -1], ['comma', ','],
    ['ident', '、'], ['ident', 'ⓐ'], ['func', 'url'], ['other', '*'],
  ]);
  // The descriptor-negative source spells U+207B as an escape.
  assert.equal(parseSymbol(tokenize('\\207B')[0]), '⁻');
});

// ── §3 descriptor grammar: the corpus statements ────────────────────────────

test('every cssom -invalid assignment is rejected by the grammar (§7.1: ignored)', () => {
  // pad-setter-invalid
  for (const v of ['-1 "0"', '3', '3 "X" "Y"']) assert.equal(parsePad(v), null, `pad ${v}`);
  // negative-setter-invalid
  for (const v of ['X Y Z', '"X" "Y" "Z"']) assert.equal(parseNegative(v), null, `negative ${v}`);
  // prefix-suffix-setter-invalid
  for (const v of ['"(" "("', ')', '123', '")" ")"', '(', '456']) assert.equal(parseAffix(v), null, `affix ${v}`);
  // range-setter-invalid
  for (const v of ['1 2 3', '3 1', '1 infinity']) assert.equal(parseRange(v), null, `range ${v}`);
  // fallback-setter-invalid
  for (const v of ['none', 'lower-roman upper-roman']) assert.equal(parseFallback(v), null, `fallback ${v}`);
  // symbols-setter-invalid (the third, `A`, is syntactically fine — it is
  // the ALPHABETIC rule that rejects it; see the setter test below)
  for (const v of ['', '1 2 *']) assert.equal(parseSymbols(v), null, `symbols ${v}`);
  // additive-symbols-setter-invalid
  for (const v of ['', 'A B C', '1 B, 2 C, 0 A', '2 C C, 1 B, 0 A']) assert.equal(parseAdditiveSymbols(v), null, `additive ${v}`);
  // name-setter-invalid (the name grammar plus the reserved names)
  for (const v of ['', '123', 'initial', 'inherit', 'unset', 'none', 'disc', 'decimal']) {
    assert.equal(parseName(v, { forSetter: true }), null, `name ${v}`);
  }
  // system-setter-invalid: the three SYNTAX errors
  for (const v of ['123', 'extends none', 'extends decimal decimal']) assert.equal(parseSystem(v), null, `system ${v}`);
  // descriptor-negative-invalid / descriptor-pad-invalid (at-rule variants)
  for (const v of ['0', '~', "'(' 'x' ')'"]) assert.equal(parseNegative(v), null, `negative ${v}`);
  for (const v of ['-1 "X"', '"#"', '2 0']) assert.equal(parsePad(v), null, `pad ${v}`);
});

test('every cssom VALID assignment parses (the setters that take effect)', () => {
  assert.deepEqual(parsePad('3 "0"'), { length: 3, symbol: '0' });
  assert.deepEqual(parseNegative('"(" ")"'), { prefix: '(', suffix: ')' });
  assert.equal(parseAffix('"("'), '(');
  assert.deepEqual(parseRange('1 2'), [[1, 2]]);
  assert.equal(parseFallback('lower-roman'), 'lower-roman');
  assert.deepEqual(parseSymbols('A B C'), ['A', 'B', 'C']);
  assert.deepEqual(parseAdditiveSymbols('2 C, 1 B, 0 A'), [[2, 'C'], [1, 'B'], [0, 'A']]);
  assert.equal(parseName('bar', { forSetter: true }), 'bar');
  assert.deepEqual(parseSystem('fixed 0'), { system: 'fixed', first: 0 });
  assert.deepEqual(parseSystem('extends upper-alpha'), { system: 'extends', base: 'upper-alpha' });
  // `"X" 1` is the same tuple as `1 "X"` (additive-symbols-syntax.html).
  assert.deepEqual(parseAdditiveSymbols('"X" 1'), [[1, 'X']]);
  assert.deepEqual(parseRange('auto'), 'auto');
  assert.deepEqual(parseRange('infinite 5, 10 infinite'), [[-Infinity, 5], [10, Infinity]]);
});

test('setterTakesEffect applies the §7.1 rule-dependent checks', () => {
  const fixed = { system: 'fixed', first: 1 }, alphabetic = { system: 'alphabetic' }, additive = { system: 'additive' };
  // system: same algorithm ⇒ takes effect; algorithm change ⇒ ignored.
  assert.equal(setterTakesEffect('system', 'fixed 0', fixed), true);            // cssom-system-setter-1
  assert.equal(setterTakesEffect('system', 'numeric', fixed), false);           // system-setter-invalid
  assert.equal(setterTakesEffect('system', 'extends lower-roman', fixed), false);
  assert.equal(setterTakesEffect('system', 'extends upper-alpha', { system: 'extends', base: 'lower-alpha' }), true);
  // symbols: fewer than 2 on alphabetic ⇒ ignored; never on additive.
  assert.equal(setterTakesEffect('symbols', 'A', alphabetic), false);          // symbols-setter-invalid
  assert.equal(setterTakesEffect('symbols', 'A B C', alphabetic), true);        // symbols-setter
  assert.equal(setterTakesEffect('symbols', 'A B C', additive), false);
  assert.equal(setterTakesEffect('additiveSymbols', '2 C, 1 B, 0 A', additive), true);
  assert.equal(setterTakesEffect('additiveSymbols', '2 C, 1 B, 0 A', fixed), false);
  // An unknown setter never counts.
  assert.equal(setterTakesEffect('colour', 'red', fixed), false);
});

// ── rule extraction + table build ───────────────────────────────────────────

test('extractCounterStyleRules keeps the LAST VALID value per descriptor', () => {
  // descriptor-pad-invalid: `pad: 3 "0"` survives three invalid re-declarations.
  const [r] = extractCounterStyleRules(`<style id="s">@counter-style a { system: extends decimal; pad: 3 "0"; pad: -1 "X"; pad: "#"; pad: 2 0; }</style>`);
  assert.equal(r.name, 'a');
  assert.equal(r.styleId, 's');
  assert.deepEqual(r.decls.pad, { length: 3, symbol: '0' });
  // A rule inside a <script> template literal is NOT a document-scope rule.
  assert.equal(extractCounterStyleRules('<script>x = `<style>@counter-style z { system: fixed; symbols: A; }</style>`</script>').length, 0);
  // Reserved names define nothing (but keep their index for the CSSOM map).
  const [d] = extractCounterStyleRules('<style>@counter-style decimal { system: cyclic; symbols: x; }</style>');
  assert.equal(d.invalidName, true);
});

test('buildAuthorTable: extends copies the base, overrides apply, invalid rules vanish', () => {
  const t = buildAuthorTable(extractCounterStyleRules(`<style>
    @counter-style pad3 { system: extends decimal; pad: 3 '0'; }
    @counter-style neg { system: extends decimal; negative: '(' ')'; }
    @counter-style aff { system: cyclic; symbols: A B C; prefix: '('; suffix: ')'; }
    @counter-style trail { system: extends decimal; suffix: ", "; }
    @counter-style bad-extends { system: extends decimal; symbols: A; }
    @counter-style no-symbols { system: alphabetic; }
    @counter-style one-symbol { system: numeric; symbols: A; }
    @counter-style my-disc { system: extends disc; }
    @counter-style loop-a { system: extends loop-b; } @counter-style loop-b { system: extends loop-a; }
    @counter-style ghost { system: extends nowhere; }
  </style>`));
  assert.equal(t.pad3.system, 'numeric');
  assert.deepEqual(t.pad3.pad, { length: 3, symbol: '0' });
  assert.equal(t.pad3.suffix, '.');
  assert.deepEqual(t.neg.negative, { prefix: '(', suffix: ')' });
  assert.deepEqual([t.aff.prefix, t.aff.suffix, t.aff.symbols], ['(', ')', ['A', 'B', 'C']]);
  // SUFFIX NOTE: the natives own the trailing gap, so `", "` is stored as ",".
  assert.equal(t.trail.suffix, ',');
  // §3.1.1 invalid rules define nothing.
  for (const name of ['bad-extends', 'no-symbols', 'one-symbol']) assert.equal(t[name], undefined, name);
  // extends a bullet ⇒ a bullet (out of the bake's scope).
  assert.deepEqual(t['my-disc'], { bullet: 'disc' });
  // A cycle and an unknown base both behave as `extends decimal`.
  assert.equal(t['loop-a'].system, 'numeric');
  assert.equal(t.ghost.system, 'numeric');
});

// ── the three systems only author rules reach ───────────────────────────────

test('cyclic wraps for every integer, fixed falls back outside its run, symbolic repeats', () => {
  assert.deepEqual([1, 2, 3, 4, 0, -1].map((v) => cyclicRepresentation(v, ['A', 'B', 'C'])), ['A', 'B', 'C', 'A', 'C', 'B']);
  assert.deepEqual([1, 2, 3].map((v) => fixedRepresentation(v, ['A', 'B'], 1)), ['A', 'B', null]);
  assert.equal(fixedRepresentation(4, ['d', 'e', 'f'], 4), 'd');                // descriptor-fallback `fixed 4`
  assert.deepEqual([1, 3, 4, 6].map((v) => symbolicRepresentation(v, ['a', 'b', 'c'])), ['a', 'c', 'aa', 'cc']);
});

// ── the CSSOM verdict on the corpus scripts ─────────────────────────────────

const rulesFor = (css) => extractCounterStyleRules(`<style id="sheet">${css}</style>`);
const script = (body) => `<style id="sheet"></style><script>
// Force layout update before changing the rule
document.body.offsetWidth;
const sheet = document.getElementById('sheet');
const foo_rule = sheet.sheet.rules[0];
${body}
</script>`;

test('cssomScriptVerdict: no script / foreign script / inert setters / effective setter', () => {
  assert.deepEqual(cssomScriptVerdict('<ol><li></ol>', []), { kind: 'none' });
  // attachShadow (the four shadow-DOM tests) and an external src are foreign.
  assert.equal(cssomScriptVerdict('<script>document.getElementById("host").attachShadow({mode: "open"}).innerHTML = `x`</script>', []).kind, 'foreign');
  assert.equal(cssomScriptVerdict('<script src="/resources/testharness.js"></script>', []).kind, 'foreign');
  // A setter on a rule the sheet map cannot resolve is foreign too.
  assert.equal(cssomScriptVerdict(script(`foo_rule.pad = '3 "0"';`), []).kind, 'foreign');
  // pad-setter-invalid: three rejected values ⇒ cssom, mutates false.
  const rules = rulesFor(`@counter-style foo { system: extends decimal; pad: 3 '0'; }`);
  const inert = cssomScriptVerdict(script(`foo_rule.pad = '-1 "0"';\nfoo_rule.pad = '3';\nfoo_rule.pad = '3 "X" "Y"';`), rules);
  assert.equal(inert.kind, 'cssom');
  assert.equal(inert.mutates, false);
  assert.equal(inert.assignments.length, 3);
  // pad-setter: one effective value ⇒ mutates.
  assert.equal(cssomScriptVerdict(script(`foo_rule.pad = '3 "0"';`), rules).mutates, true);
  // fallback-setter-invalid drops the trailing semicolon on its last line.
  const fb = rulesFor(`@counter-style foo { system: fixed; symbols: A B; fallback: lower-roman; }`);
  assert.equal(cssomScriptVerdict(script(`foo_rule.fallback = 'none';\nfoo_rule.fallback = 'lower-roman upper-roman'`), fb).mutates, false);
  // system-setter-invalid: syntax errors AND algorithm changes are all inert.
  const fx = rulesFor(`@counter-style foo { system: fixed; symbols: A B C; }`);
  assert.equal(cssomScriptVerdict(script(`foo_rule.system = '123';\nfoo_rule.system = 'extends none';\nfoo_rule.system = 'numeric';\nfoo_rule.system = 'extends lower-roman';`), fx).mutates, false);
  // name-setter: `sheet.sheet.rules[2]` names the THIRD rule of that sheet.
  const nm = rulesFor(`@counter-style a { system: fixed; symbols: A; } @counter-style b { system: fixed; symbols: B; } @counter-style foo { system: fixed; symbols: C; }`);
  const named = cssomScriptVerdict(`<style id="sheet"></style><script>
document.body.offsetWidth;
const sheet = document.getElementById('sheet');
const rule = sheet.sheet.rules[2];
rule.name = 'bar';
</script>`, nm);
  assert.equal(named.mutates, true);
});
