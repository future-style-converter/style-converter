// tools/titan/attr-bake.test.mjs — unit pins for the wave-25 ATTR BAKE
// (attr-bake.mjs; the three call sites it is wired into live in
// extract-fixture.mjs and are pinned at the bottom of this file).
//
// Coverage map (mirrors the module's contract sections):
//   1. the conservative trigger — the ONE thing that makes the non-attr
//      corpus byte-identical by construction;
//   2. the value scanner (findAttrCall / splitFirstTopLevelComma): nesting,
//      strings, unbalanced parens, the one-comma rule;
//   3. argument parsing (parseAttrName / parseAttrType / parseAttrArgs),
//      including every documented BAIL;
//   4. the casts (castAttrValue) — <length>'s unitless zero, <color>'s
//      accept/reject, the <attr-unit> number cast, raw-string quoting;
//   5. resolution (resolveAttrCall): attribute hit, fallback, default
//      fallback, guaranteed-invalid, nested fallbacks + the depth cap;
//   6. whole-value substitution + the single-arg math fold;
//   7. bakeAttr's mutation contract and its three LOUD markers;
//   8. THE PROVING SET — the six css-values wall tests, each pinned to the
//      declaration + attribute its HTML actually carries, plus the two
//      namespace tests that must come out UNCHANGED;
//   9. source-wiring pins for the extract-fixture.mjs integration.

import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

import {
  ATTR_BAKED_REASON, ATTR_UNRESOLVED_REASON, ATTR_IACVT_REASON,
  hasAttrFunction, findAttrCall, splitFirstTopLevelComma,
  parseAttrName, parseAttrType, parseAttrArgs,
  castAttrValue, cssString, resolveAttrCall,
  isDeclarationValue, isCssStringToken, isSpliceSafe,
  substituteAttrInValue, foldSingleArgMathWrappers, bakeAttr,
} from './attr-bake.mjs';

const __dirname = dirname(fileURLToPath(import.meta.url));

// ── 1. the conservative trigger ─────────────────────────────────────────────

test('trigger: fires on an attr() token, in any position', () => {
  assert.equal(hasAttrFunction('attr(data-x)'), true);
  assert.equal(hasAttrFunction('max(attr(data-x type(<length>)))'), true);
  assert.equal(hasAttrFunction('ATTR(data-x)'), true);
});

test('trigger: does NOT fire without a call — the corpus-identity guarantee', () => {
  // No attr() token anywhere: plain values, the bare word, and an ident
  // that merely ENDS in "attr" (the negative lookbehind's job).
  assert.equal(hasAttrFunction('200px'), false);
  assert.equal(hasAttrFunction('attr'), false);
  assert.equal(hasAttrFunction('var(--data-attr)'), false);
  assert.equal(hasAttrFunction('url(x-attr(1))'), false);
  // Non-strings can never carry a call.
  assert.equal(hasAttrFunction(42), false);
  assert.equal(hasAttrFunction(null), false);
});

// ── 2. the value scanner ────────────────────────────────────────────────────

test('findAttrCall: reports the argument span and the end of the call', () => {
  const v = 'max(attr(data-test type(<length>)))';
  const c = findAttrCall(v);
  assert.equal(v.slice(c.start, c.end), 'attr(data-test type(<length>))');
  assert.equal(v.slice(c.argsStart, c.argsEnd), 'data-test type(<length>)');
});

test('findAttrCall: nested parens do not end the call early', () => {
  const v = 'attr(x type(<color>), rgb(1, 2, 3))';
  const c = findAttrCall(v);
  assert.equal(c.end, v.length);
  assert.equal(v.slice(c.argsStart, c.argsEnd), 'x type(<color>), rgb(1, 2, 3)');
});

test('findAttrCall: a call inside a CSS string is literal text, not a call', () => {
  assert.equal(findAttrCall('"attr(x)"'), null);
  assert.equal(findAttrCall("'attr(x)' attr(y)").start, 10);
  // Escaped quote inside the string must not end it early.
  assert.equal(findAttrCall('"a\\"attr(x)"'), null);
});

test('findAttrCall: unbalanced parens report "no call" so the caller bails', () => {
  assert.equal(findAttrCall('attr(x'), null);
});

test('splitFirstTopLevelComma: splits ONCE, past nested commas', () => {
  assert.deepEqual(splitFirstTopLevelComma('x type(<color>), rgb(1, 2, 3)'),
    ['x type(<color>)', ' rgb(1, 2, 3)']);
  assert.deepEqual(splitFirstTopLevelComma('x type(<length>)'),
    ['x type(<length>)', null]);
  // A comma inside a string is not a separator.
  assert.deepEqual(splitFirstTopLevelComma('x, "a, b"'), ['x', ' "a, b"']);
});

// ── 3. argument parsing ─────────────────────────────────────────────────────

test('parseAttrName: lowercases (HTML attribute names are case-insensitive)', () => {
  assert.equal(parseAttrName('data-Test'), 'data-test');
  assert.equal(parseAttrName('CoLoR-attr'), 'color-attr');
  // Non-ASCII idents are legal — css-values/html-attr-case-insensitivity
  // really does test `attr(CØLØR-ÅTTR type(<color>))`.
  assert.equal(parseAttrName('CØLØR-ÅTTR'), 'cølør-åttr');
});

test('parseAttrName: EVERY namespace-qualified form bails', () => {
  // The wildcard bail is load-bearing: css-values/attr-namespace-wildcard
  // asserts "Wildcard not supported in attr() function".
  assert.equal(parseAttrName('*|bar'), null);
  assert.equal(parseAttrName('foo|bar'), null);
  assert.equal(parseAttrName('|bar'), null);
});

test('parseAttrType: omitted and explicit raw-string, type(), <attr-unit>', () => {
  assert.deepEqual(parseAttrType(''), { kind: 'raw-string' });
  assert.deepEqual(parseAttrType('raw-string'), { kind: 'raw-string' });
  assert.deepEqual(parseAttrType('type(<length>)'), { kind: 'syntax', syntax: '<length>' });
  assert.deepEqual(parseAttrType('type( <COLOR> )'), { kind: 'syntax', syntax: '<color>' });
  assert.deepEqual(parseAttrType('px'), { kind: 'unit', unit: 'px' });
  assert.deepEqual(parseAttrType('%'), { kind: 'unit', unit: '%' });
});

test('parseAttrType: unions, multipliers and unknown units bail', () => {
  assert.equal(parseAttrType('type(<length> | <percentage>)'), null);
  assert.equal(parseAttrType('type(<length>+)'), null);
  assert.equal(parseAttrType('type(<image>)'), null);
  assert.equal(parseAttrType('bananas'), null);
});

test('parseAttrArgs: name / type / fallback split', () => {
  assert.deepEqual(parseAttrArgs('data-test type(<length>), 200px'),
    { name: 'data-test', type: { kind: 'syntax', syntax: '<length>' }, fallback: '200px' });
  assert.deepEqual(parseAttrArgs('data-mark'),
    { name: 'data-mark', type: { kind: 'raw-string' }, fallback: null });
});

test('parseAttrArgs: an empty name or an explicitly-empty fallback bails', () => {
  assert.equal(parseAttrArgs(''), null);
  assert.equal(parseAttrArgs('x,'), null);
  assert.equal(parseAttrArgs('x,   '), null);
});

// ── 4. the casts ────────────────────────────────────────────────────────────

test('cast <length>: units, sign, and the UNITLESS ZERO', () => {
  const t = { kind: 'syntax', syntax: '<length>' };
  assert.equal(castAttrValue('200px', t), '200px');
  assert.equal(castAttrValue('-1.5em', t), '-1.5em');
  // css-values-4 §5: a zero length may be written without a unit. This is
  // exactly what attr-length-valid-zero asserts.
  assert.equal(castAttrValue('0', t), '0');
  assert.equal(castAttrValue(' 0.0 ', t), '0.0');
  // A NON-zero unitless number is not a length.
  assert.equal(castAttrValue('200', t), null);
  assert.equal(castAttrValue('qqffuutt', t), null);
  assert.equal(castAttrValue('', t), null);
});

test('cast <color>: keywords, hex, functions — and the invalid-cast reject', () => {
  const t = { kind: 'syntax', syntax: '<color>' };
  assert.equal(castAttrValue('green', t), 'green');
  assert.equal(castAttrValue('GREEN', t), 'GREEN');       // text spliced verbatim
  assert.equal(castAttrValue('rebeccapurple', t), 'rebeccapurple');
  assert.equal(castAttrValue('currentcolor', t), 'currentcolor');
  assert.equal(castAttrValue('#0f0', t), '#0f0');
  assert.equal(castAttrValue('oklch(0.5 0.1 20)', t), 'oklch(0.5 0.1 20)');
  // The attribute attr-color-invalid-cast actually carries.
  assert.equal(castAttrValue('qqffuutt', t), null);
  assert.equal(castAttrValue('#12345', t), null);         // 5-digit hex is invalid
});

test('cast <attr-unit>: a bare <number> gains the unit', () => {
  assert.equal(castAttrValue('12', { kind: 'unit', unit: 'px' }), '12px');
  assert.equal(castAttrValue('-.5', { kind: 'unit', unit: 'deg' }), '-.5deg');
  // Already-dimensioned text is NOT a <number> and fails the cast.
  assert.equal(castAttrValue('12px', { kind: 'unit', unit: 'px' }), null);
});

test('cast raw-string: raw attribute text is QUOTED', () => {
  assert.equal(castAttrValue('Fallback value', { kind: 'raw-string' }), '"Fallback value"');
  assert.equal(castAttrValue('a"b\\c', { kind: 'raw-string' }), '"a\\"b\\\\c"');
  // raw-string quotes whatever it is given — including text that already
  // looks like a string, which then nests (Chrome 150: attr(x raw-string)
  // with x='"abc"' computes the string `"abc"`, quotes included).
  assert.equal(castAttrValue('"abc"', { kind: 'raw-string' }), '"\\"abc\\""');
  // A literal newline is invalid inside a CSS string → hex escape (§4.3.5).
  assert.equal(cssString('a\nb'), '"a\\a b"');
});

test('cast type(<string>): the ATTRIBUTE must already be a quoted CSS string', () => {
  const c = (v) => castAttrValue(v, { kind: 'syntax', syntax: '<string>' });
  // css-values-5 §7.1 parses the attribute text AS CSS for every
  // type(<syntax>) cast, so <string> is NOT a synonym for raw-string.
  // Both directions measured against Chrome 150 (see the SYNTAX_VALIDATORS
  // banner): bare text FAILS the cast, and an already-quoted attribute is
  // spliced VERBATIM rather than re-quoted.
  assert.equal(c('hi'), null);            // browser: cast fails → fallback/IACVT
  assert.equal(c('"abc"'), '"abc"');      // browser: the string `abc`
  assert.equal(c("'sq'"), "'sq'");        // single quotes are a string token too
  assert.equal(c('"a" "b"'), null);       // two tokens, not one <string>
  assert.equal(c('"a" ; "b"'), null);     // trailing junk after the string
  assert.equal(c('"unterminated'), null); // <bad-string-token>
  // The predicate the cast delegates to, pinned directly: exactly ONE
  // complete <string-token> and nothing else.
  assert.equal(isCssStringToken('"abc"'), true);
  assert.equal(isCssStringToken('"a\\"b"'), true);   // escaped quote inside
  assert.equal(isCssStringToken('"a"b'), false);     // trailing junk
  assert.equal(isCssStringToken('"a\nb"'), false);   // raw newline
  assert.equal(isCssStringToken('abc'), false);
  assert.equal(isCssStringToken('""'), true);        // the empty string
});

test('type(*) enforces <declaration-value>, not just non-emptiness', () => {
  const c = (v) => castAttrValue(v, { kind: 'syntax', syntax: '*' });
  // Every rejection below was measured as a cast FAILURE in Chrome 150; a
  // permissive `!== ''` check baked them, and each is also a serialization
  // hazard once spliced back into a declaration.
  assert.equal(c('200px; color: red'), null);      // top-level semicolon
  assert.equal(c('200px} #zz{width:1px'), null);   // unmatched `}` — rule escape
  assert.equal(c('red !important'), null);         // top-level `!`
  assert.equal(c('a)b'), null);                    // unmatched closer
  assert.equal(c('"a\nb"'), null);                 // <bad-string-token>
  // …and the legal shapes still pass, including a `;` INSIDE a string and
  // matched block brackets.
  assert.equal(c('"semi;colon"'), '"semi;colon"');
  assert.equal(c('a[b]c'), 'a[b]c');
  assert.equal(c('calc(10px + 5px)'), 'calc(10px + 5px)');
});

test('isDeclarationValue keeps auto-closed openers LEGAL (splice-safety is separate)', () => {
  // css-syntax-3 §4.3.2 auto-closes a function at EOF, and Chrome 150
  // computes `width: attr(x type(*))` with x="calc(10px + 5px" as 15px —
  // so the grammar check must NOT reject it…
  assert.equal(isDeclarationValue('calc(10px + 5px'), true);
  assert.equal(isDeclarationValue('url(unclosed'), true);
  // …but the value cannot be SPLICED, because it would swallow the
  // declaration's own `;`. That is isSpliceSafe's job, and it routes the
  // call to the verbatim/'attr-unresolved' bail rather than to a fallback.
  assert.equal(isSpliceSafe('calc(10px + 5px'), false);
  assert.equal(isSpliceSafe('calc(10px + 5px)'), true);
  assert.equal(isSpliceSafe('"a(b"'), true);            // opener inside a string
  assert.equal(isSpliceSafe('"unterminated'), false);
  assert.deepEqual(
    resolveAttrCall({ name: 'x', type: { kind: 'syntax', syntax: '*' }, fallback: '5px' },
      { x: 'calc(10px + 5px' }),
    { status: 'unsupported' },
    'splice-unsafe text bails verbatim — it does NOT fall through to the fallback',
  );
});

test('cast <color>: a function name alone is not enough — args must be token-clean', () => {
  const c = (v) => castAttrValue(v, { kind: 'syntax', syntax: '<color>' });
  assert.equal(c('rgb(0 128 0)'), 'rgb(0 128 0)');
  assert.equal(c('color-mix(in srgb, red, blue)'), 'color-mix(in srgb, red, blue)');
  // Chrome 150: `attr(x type(<color>), green)` with x="rgb(1;2)" computes
  // GREEN — the cast fails and the fallback is taken. Accepting the function
  // on its name alone baked `rgb(1;2)` instead (transparent, plus a `;`
  // smuggled into a declaration value).
  assert.equal(c('rgb(1;2)'), null);
  assert.equal(c('rgb(1!2)'), null);
});

test('cast <number> / <integer> / <percentage> / <angle> / <time> / *', () => {
  const c = (v, s) => castAttrValue(v, { kind: 'syntax', syntax: s });
  assert.equal(c('1.5', '<number>'), '1.5');
  assert.equal(c('1.5', '<integer>'), null);
  assert.equal(c('50%', '<percentage>'), '50%');
  assert.equal(c('90deg', '<angle>'), '90deg');
  assert.equal(c('200ms', '<time>'), '200ms');
  assert.equal(c('200s', '<time>'), '200s');
  assert.equal(c('red', '*'), 'red');
  assert.equal(c('  ', '*'), null);
});

// ── 5. resolution ───────────────────────────────────────────────────────────

const lengthArgs = (fallback = null) =>
  ({ name: 'data-test', type: { kind: 'syntax', syntax: '<length>' }, fallback });

test('resolve: attribute present and castable wins', () => {
  assert.deepEqual(resolveAttrCall(lengthArgs('9px'), { 'data-test': '200px' }),
    { status: 'ok', text: '200px' });
});

test('resolve: a failed cast falls back (the invalid-cast rule)', () => {
  assert.deepEqual(resolveAttrCall(lengthArgs('200px'), { 'data-test': 'qqffuutt' }),
    { status: 'ok', text: '200px' });
});

test('resolve: a MISSING attribute falls back', () => {
  assert.deepEqual(resolveAttrCall(lengthArgs('200px'), {}),
    { status: 'ok', text: '200px' });
});

test('resolve: no fallback + typed = guaranteed-invalid (IACVT)', () => {
  assert.deepEqual(resolveAttrCall(lengthArgs(null), {}), { status: 'invalid' });
});

test('resolve: no fallback + raw-string = the empty string', () => {
  const args = { name: 'nope', type: { kind: 'raw-string' }, fallback: null };
  assert.deepEqual(resolveAttrCall(args, {}), { status: 'ok', text: '""' });
});

test('resolve: an attribute carrying var()/attr()/env() bails, never half-resolves', () => {
  const args = { name: 'x', type: { kind: 'raw-string' }, fallback: null };
  assert.deepEqual(resolveAttrCall(args, { x: 'var(--a)' }), { status: 'unsupported' });
  assert.deepEqual(resolveAttrCall(args, { x: 'attr(y)' }), { status: 'unsupported' });
});

test('resolve: a fallback may itself contain attr() (recursive), and may poison', () => {
  const nested = {
    name: 'miss', type: { kind: 'syntax', syntax: '<color>' },
    fallback: 'attr(data-c type(<color>))',
  };
  assert.deepEqual(resolveAttrCall(nested, { 'data-c': 'green' }),
    { status: 'ok', text: 'green' });
  // Nested call has no value and no fallback → guaranteed-invalid, which
  // propagates out through the outer call.
  assert.deepEqual(resolveAttrCall(nested, {}), { status: 'invalid' });
});

test('resolve: the fallback recursion depth is capped, not unbounded', () => {
  const args = { name: 'miss', type: { kind: 'raw-string' }, fallback: 'attr(a)' };
  assert.deepEqual(resolveAttrCall(args, { a: 'x' }, 8), { status: 'unsupported' });
});

// ── 6. whole-value substitution + the fold ──────────────────────────────────

test('substitute: several calls in one value, each resolved independently', () => {
  const r = substituteAttrInValue(
    '1px solid attr(data-c type(<color>)) attr(data-n px)',
    { 'data-c': 'green', 'data-n': '3' },
  );
  assert.deepEqual(r, { value: '1px solid green 3px', baked: true, unresolved: false, invalid: false });
});

test('substitute: an unmodelled call is copied through VERBATIM and flagged', () => {
  const src = 'attr(foo|bar type(*), green) attr(data-c type(<color>))';
  const r = substituteAttrInValue(src, { 'data-c': 'red' });
  assert.equal(r.value, 'attr(foo|bar type(*), green) red');
  assert.equal(r.unresolved, true);
  assert.equal(r.baked, true);
});

test('substitute: guaranteed-invalid stops the pass and reports invalid', () => {
  const r = substituteAttrInValue('attr(data-test type(<length>))', {});
  assert.equal(r.invalid, true);
});

test('substitute: text around and between calls is preserved exactly', () => {
  const r = substituteAttrInValue('calc(attr(a px) + 1px)', { a: '2' });
  assert.equal(r.value, 'calc(2px + 1px)');
});

test('fold: single-argument min/max/calc collapse; real calculations do not', () => {
  assert.equal(foldSingleArgMathWrappers('max(200px)'), '200px');
  assert.equal(foldSingleArgMathWrappers('min(0)'), '0');
  assert.equal(foldSingleArgMathWrappers('calc(max(1px))'), '1px');
  // Anything with an operator is a real calculation and is left alone.
  assert.equal(foldSingleArgMathWrappers('calc(2px + 1px)'), 'calc(2px + 1px)');
  assert.equal(foldSingleArgMathWrappers('max(1px, 2px)'), 'max(1px, 2px)');
  assert.equal(foldSingleArgMathWrappers('calc(100% - 10px)'), 'calc(100% - 10px)');
  // A nested function body is not a bare operand.
  assert.equal(foldSingleArgMathWrappers('max(var(--x))'), 'max(var(--x))');
});

// ── 7. bakeAttr: mutation contract + the three markers ──────────────────────

test('bakeAttr: mutates in place and reports baked', () => {
  const props = { width: 'attr(data-test type(<length>), 200px)', height: '200px' };
  const r = bakeAttr(props, { 'data-test': 'qqffuutt' });
  assert.deepEqual(props, { width: '200px', height: '200px' });
  assert.deepEqual(r, { baked: true, unresolved: false, iacvt: false });
});

test('bakeAttr: an attr()-free bag is untouched and reports nothing', () => {
  const props = { width: '100px', background: 'linear-gradient(red, blue)' };
  const before = { ...props };
  assert.deepEqual(bakeAttr(props, { 'data-test': '1px' }),
    { baked: false, unresolved: false, iacvt: false });
  assert.deepEqual(props, before);
});

test('bakeAttr: IACVT rewrites the declaration to `unset`, never deletes it', () => {
  // Deleting would hand the cascade back to an earlier same-property
  // declaration — the exact behaviour css-values-5 forbids.
  const props = { width: 'attr(data-test type(<length>))' };
  const r = bakeAttr(props, {});
  assert.deepEqual(props, { width: 'unset' });
  assert.deepEqual(r, { baked: false, unresolved: false, iacvt: true });
});

test('bakeAttr: the three markers are independent within one bag', () => {
  const props = {
    color: 'attr(data-c type(<color>))',        // bakes
    background: 'attr(*|bar type(*))',          // bails verbatim
    width: 'attr(data-w type(<length>))',       // IACVT
  };
  const r = bakeAttr(props, { 'data-c': 'green' });
  assert.deepEqual(props, {
    color: 'green', background: 'attr(*|bar type(*))', width: 'unset',
  });
  assert.deepEqual(r, { baked: true, unresolved: true, iacvt: true });
});

test('bakeAttr: a null/absent attribute bag is treated as "no attributes"', () => {
  const props = { content: 'attr(data-mark, "fallback")' };
  assert.deepEqual(bakeAttr(props, null), { baked: true, unresolved: false, iacvt: false });
  assert.deepEqual(props, { content: '"fallback"' });
});

test('bakeAttr: the marker strings are the ones extract-fixture pushes', () => {
  assert.equal(ATTR_BAKED_REASON, 'baked-attr');
  assert.equal(ATTR_UNRESOLVED_REASON, 'attr-unresolved');
  assert.equal(ATTR_IACVT_REASON, 'attr-invalid-at-computed-value-time');
});

// ── 8. THE PROVING SET — the six css-values wall tests ──────────────────────
//
// Each row is the EXACT declaration + attribute the test's HTML carries and
// the value the test's reference (200-200-green.html: a 200×200 green
// square on white, nothing else) demands. These six all scored 0.803–0.906
// against the browser ref on ALL THREE platforms before the bake.

const PROVING_SET = [
  // attr-color-valid — `background: attr(data-test type(<color>))`, green.
  ['attr-color-valid', 'background', 'attr(data-test type(<color>))',
    { 'data-test': 'green' }, 'green'],
  // attr-color-invalid-cast — `qqffuutt` fails the <color> cast → fallback.
  ['attr-color-invalid-cast', 'background', 'attr(data-test type(<color>), green)',
    { 'data-test': 'qqffuutt' }, 'green'],
  // attr-in-max — attr() inside max(); the degenerate one-arg max folds.
  ['attr-in-max', 'width', 'max(attr(data-test type(<length>)))',
    { 'data-test': '200px' }, '200px'],
  // attr-length-invalid-cast — `qqffuutt` fails <length> → 200px fallback.
  ['attr-length-invalid-cast', 'width', 'attr(data-test type(<length>), 200px)',
    { 'data-test': 'qqffuutt' }, '200px'],
  // attr-length-valid-zero — "0" IS a valid <length>; the red #outer2
  // collapses to zero width instead of laying out at auto.
  ['attr-length-valid-zero', 'width', 'attr(data-test type(<length>), 0)',
    { 'data-test': '0' }, '0'],
  // …and the same with no fallback at all: still valid, still zero.
  ['attr-length-valid-zero-nofallback', 'width', 'attr(data-test type(<length>))',
    { 'data-test': '0' }, '0'],
];

for (const [name, prop, decl, attrs, expected] of PROVING_SET) {
  test(`PROVING SET: ${name} resolves ${prop} to ${expected}`, () => {
    const props = { [prop]: decl };
    const r = bakeAttr(props, attrs);
    assert.equal(props[prop], expected);
    assert.equal(r.baked, true);
    assert.equal(r.iacvt, false);
    assert.equal(r.unresolved, false);
  });
}

test('PROVING SET: the six declarations are still exactly what WPT ships', (t) => {
  // Guards against the bake being pinned to a stale reading of the corpus:
  // if WPT rewrites one of these tests, this fails LOUDLY instead of the
  // proving set quietly proving the wrong thing. Skipped when the WPT
  // mirror is absent (it is gitignored — tools/titan/fetch-wpt.sh).
  const wptDir = process.env.WPT_DIR ?? join(__dirname, '..', 'wpt');
  let html;
  try {
    html = readFileSync(join(wptDir, 'css', 'css-values', 'attr-color-valid.html'), 'utf8');
  } catch {
    // retro R8b (A8#2): SKIP visibly — never a bare `return`. The return made
    // this test report PASS with ZERO assertions executed on every CI run
    // (ci.yml's test-tooling job has no tools/wpt mirror), so the proving set
    // looked green while proving nothing there. `t.skip` is the idiom the
    // other corpus-gated pins already use (capture-divergence.test.mjs:150,
    // extract-fixture.test.mjs:5321): the runner reports `# skipped 1`.
    t.skip('WPT mirror absent (run tools/titan/fetch-wpt.sh) — proving set not exercised');
    return; // the unit pins above still hold; this one is honestly unrun
  }
  assert.ok(html.includes('background: attr(data-test type(<color>))'));
  assert.ok(html.includes('data-test="green"'));
  for (const [name, prop, decl, attrs] of PROVING_SET) {
    const src = readFileSync(join(wptDir, 'css', 'css-values', `${name}.html`), 'utf8');
    assert.ok(src.includes(`${prop}: ${decl}`), `${name}: declaration drifted`);
    const [attrName, attrValue] = Object.entries(attrs)[0];
    assert.ok(src.includes(`${attrName}="${attrValue}"`), `${name}: attribute drifted`);
  }
});

test('PROVING SET: the two namespace tests come out with UNCHANGED properties', () => {
  // attr-namespace-wildcard asserts wildcards are NOT supported (resolving
  // `*|bar` to "red" would paint the box the test forbids); attr-namespace-
  // non-existing needs an @namespace table the extractor does not build.
  // Both must therefore ship their declaration byte-for-byte as authored.
  const wildcard = { background: 'attr(*|bar type(*))' };
  assert.deepEqual(bakeAttr(wildcard, { bar: 'red' }),
    { baked: false, unresolved: true, iacvt: false });
  assert.equal(wildcard.background, 'attr(*|bar type(*))');

  const nonExisting = { background: 'attr(foo|bar type(*), green)' };
  assert.deepEqual(bakeAttr(nonExisting, { bar: 'red' }),
    { baked: false, unresolved: true, iacvt: false });
  assert.equal(nonExisting.background, 'attr(foo|bar type(*), green)');
});

// ── 9. extract-fixture.mjs wiring pins ──────────────────────────────────────

const EXTRACTOR_SRC = readFileSync(join(__dirname, 'extract-fixture.mjs'), 'utf8');

test('wiring: extract-fixture imports the bake and all three markers', () => {
  assert.match(EXTRACTOR_SRC, /from '\.\/attr-bake\.mjs'/);
  for (const sym of ['bakeAttr', 'hasAttrFunction',
    'ATTR_BAKED_REASON', 'ATTR_UNRESOLVED_REASON', 'ATTR_IACVT_REASON']) {
    assert.ok(EXTRACTOR_SRC.includes(sym), `missing import/use of ${sym}`);
  }
});

test('wiring: the element bag and the pseudo bag both bake against node.attrs', () => {
  // css-values-5 §7 resolves a pseudo-element's attr() against the
  // ORIGINATING element, which is the host node's own attribute bag.
  assert.match(EXTRACTOR_SRC, /const attrBaked = bakeAttr\(props, node\.attrs\);/);
  assert.match(EXTRACTOR_SRC, /const peAttrBaked = bakeAttr\(peProps, node\.attrs\);/);
});

test('wiring: the synthetic body-root scope is NOT baked, only flagged', () => {
  // That bag merges html/body/:root/* rules and has no single originating
  // element, so it reports 'attr-unresolved' rather than guessing one.
  assert.match(EXTRACTOR_SRC,
    /Object\.values\(root\.props\)\.some\(hasAttrFunction\)/);
  assert.ok(!/bakeAttr\(root\.props/.test(EXTRACTOR_SRC));
});

test('wiring: the bake runs BEFORE the lossy scan on the element path', () => {
  // Ordering matters: a baked `2em` must still trip the em/rem lane, and a
  // baked `0` must no longer read as an unresolved attr().
  const bakeAt = EXTRACTOR_SRC.indexOf('const attrBaked = bakeAttr(props, node.attrs);');
  const scanAt = EXTRACTOR_SRC.indexOf('const reasons = lossyReasonsFor(props);');
  assert.ok(bakeAt > 0 && scanAt > bakeAt, 'attr bake must precede lossyReasonsFor');
});
