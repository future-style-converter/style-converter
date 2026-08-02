#!/usr/bin/env node
//
// Unit tests for tools/titan/extract-fixture.mjs.
//
// Pinned via Node's built-in test runner. Pattern mirrors
// tools/visual/css-to-ir.test.mjs (round 56) so the smoke harness runs both
// suites with one `node --test tools/visual/**/*.test.mjs`.
//
// Coverage targets the pure helpers — file IO is exercised by the
// orchestrator's smoke run, not here, so these tests stay <100 ms.

import { test } from 'node:test';
import assert from 'node:assert/strict';

import {
  stripComments,
  extractInlineStyle,
  extractLinkedStylesheets,
  extractFuzzy,
  extractRefHref,
  parseCss,
  extractBodyChildren,
  extractBodyTree,
  extractBodyTreeNested,
  extractOwnText,
  // wave-11 fix 2: leading anonymous body text.
  extractLeadingBodyText,
  // wave-12 EXTRACTOR-INLINE: pure-inline run merging.
  extractOwnTextMerged,
  extractLeadingBodyTextInfo,
  isPureInlineMergeable,
  collectStyledTags,
  // wave-15 BIDI-EXTRACT: character-reference decode + dir attribute.
  decodeCharacterReferences,
  firstStrongDirection,
  dirAttributeDirection,
  selectorMatches,
  selectorMatchesPseudoElement,
  propsForElement,
  propsForBodyRoot,
  buildComponents,
  // wave-17 BODY-HEIGHT SLOTTING: the explicit-absolute-height trigger.
  bodyDeclaresAbsoluteHeight,
  splitAnBOfSelector,
  collectDefinedTags,
  // wave-8: child combinator + support-asset inlining.
  splitSelectorChain,
  percentEncodeBytes,
  inlineUrlsInValue,
  inlineFixtureAssets,
  MAX_INLINE_ASSET_BYTES,
  // wave-13 KEYFRAMES-SAMPLER: static @keyframes sampling.
  parseKeyframes,
  parseTimingFunction,
  evalTimingFunction,
  cubicBezierY,
  parseSrgbColor,
  lerpSrgbColors,
  lerpCssValue,
  parseAnimationDecl,
  sampleKeyframesAnimation,
  // wave-22 EX2 B-RC4a: the scoped inline-chain collapse.
  collapseInlineRun,
  decorationContribution,
  flattenInlineText,
  INLINE_CHAIN_TAGS,
  INTRINSIC_WIDGET_TAGS,
  // wave-22 EX2 SKEPTIC: displaced-decoration-modifier honesty.
  carriesUnexpressibleDecoration,
  // wave-23 BIDI BAKE: the static trigger that gates the browser launch.
  bidiBakeTrigger,
  RTL_CODEPOINT_RANGES,
  BIDI_CONTROL_CODEPOINTS,
  BIDI_DIR_ATTR_RX,
  BIDI_DIRECTION_CSS_RX,
  BIDI_UNICODE_BIDI_CSS_RX,
} from './extract-fixture.mjs';

// ── stripComments ───────────────────────────────────────────────────────────

test('stripComments removes HTML comments', () => {
  // HTML comments erase to '' — the DOM concatenates the neighbouring
  // text nodes with nothing in between (see stripComments' asymmetry note).
  assert.equal(stripComments('a <!-- x --> b'), 'a  b');
});

test('stripComments replaces CSS comments with a single space', () => {
  // wave-11 TITAN fix 1: css-syntax-3 §4.3.2 makes a comment a TOKEN
  // SEPARATOR, so it must become whitespace — not vanish. The leading
  // `/* hide */` therefore leaves a space behind (two spaces total with
  // the one already following it).
  assert.equal(stripComments('<style>/* hide */ p{color:red}</style>'),
                             '<style>  p{color:red}</style>');
});

test('stripComments keeps comment-separated hsla components apart', () => {
  // wave-11 TITAN fix 1 pin — WPT css-color background-color-hsl-001 #p5:
  // erasing the comments used to GLUE the number tokens into the corrupted
  // `hsla(12075%50%/1.0)` (2 corrupt swatches in each of the 4
  // comment-bearing css-color tests). The space replacement preserves the
  // separator role the comment played.
  assert.equal(stripComments('hsla(120/* c */75%/* c */50%/1.0)'),
                             'hsla(120 75% 50%/1.0)');
});

// ── extractInlineStyle ──────────────────────────────────────────────────────

test('extractInlineStyle concatenates multiple <style> blocks', () => {
  const html = '<style>a{color:red}</style><body><style>b{color:blue}</style></body>';
  const css = extractInlineStyle(html);
  assert.match(css, /a\{color:red\}/);
  assert.match(css, /b\{color:blue\}/);
});

test('extractInlineStyle returns empty string when no <style>', () => {
  assert.equal(extractInlineStyle('<body><div></div></body>'), '');
});

// ── extractLinkedStylesheets ────────────────────────────────────────────────

test('extractLinkedStylesheets picks up rel=stylesheet hrefs', () => {
  const html = '<link rel="stylesheet" href="../shared.css"><link rel="stylesheet" href="local.css">';
  const hrefs = extractLinkedStylesheets(html);
  assert.deepEqual(hrefs, ['../shared.css', 'local.css']);
});

test('extractLinkedStylesheets ignores non-stylesheet links', () => {
  const html = '<link rel="match" href="ref.html"><link rel="help" href="https://x">';
  assert.deepEqual(extractLinkedStylesheets(html), []);
});

// ── extractFuzzy ────────────────────────────────────────────────────────────

test('extractFuzzy parses N;N form', () => {
  const html = '<meta name="fuzzy" content="15;300">';
  const f = extractFuzzy(html);
  assert.deepEqual(f.maxDifference, { min: 15, max: 15 });
  assert.deepEqual(f.totalPixels,   { min: 300, max: 300 });
});

test('extractFuzzy parses min-max ranges', () => {
  const html = '<meta name="fuzzy" content="10-20;100-300">';
  const f = extractFuzzy(html);
  assert.deepEqual(f.maxDifference, { min: 10, max: 20 });
  assert.deepEqual(f.totalPixels,   { min: 100, max: 300 });
});

test('extractFuzzy parses key=value form', () => {
  const html = '<meta name="fuzzy" content="maxDifference=15;totalPixels=300">';
  const f = extractFuzzy(html);
  assert.deepEqual(f.maxDifference, { min: 15, max: 15 });
  assert.deepEqual(f.totalPixels,   { min: 300, max: 300 });
});

test('extractFuzzy returns null when meta absent', () => {
  assert.equal(extractFuzzy('<body></body>'), null);
});

// ── extractRefHref ──────────────────────────────────────────────────────────

test('extractRefHref returns href from rel=match', () => {
  const html = '<link rel="match" href="background-001-ref.html">';
  assert.equal(extractRefHref(html), 'background-001-ref.html');
});

test('extractRefHref tolerates href before rel', () => {
  const html = '<link href="../ref.html" rel="match">';
  assert.equal(extractRefHref(html), '../ref.html');
});

test('extractRefHref returns null when missing', () => {
  assert.equal(extractRefHref('<link rel="help" href="x">'), null);
});

// ── parseCss ────────────────────────────────────────────────────────────────

test('parseCss parses basic rule', () => {
  const rules = parseCss('.a { color: red; padding: 8px }');
  assert.equal(rules.length, 1);
  assert.equal(rules[0].selector, '.a');
  assert.equal(rules[0].props.color, 'red');
  assert.equal(rules[0].props.padding, '8px');
});

test('parseCss splits comma-separated selector list', () => {
  const rules = parseCss('.a, .b { color: red }');
  assert.equal(rules.length, 2);
  assert.equal(rules[0].selector, '.a');
  assert.equal(rules[1].selector, '.b');
});

test('parseCss strips !important', () => {
  const rules = parseCss('.a { color: red !important }');
  assert.equal(rules[0].props.color, 'red');
});

test('parseCss skips @media block wholesale', () => {
  // @media bodies aren't expanded — we treat them as conditional and
  // leave them out of the flat IR. The bucketer should have flagged
  // these earlier, but defensive parsing avoids surprises.
  const rules = parseCss('.a{color:red}@media (min-width:0){.b{color:blue}}.c{color:green}');
  const sels = rules.map((r) => r.selector);
  assert.deepEqual(sels, ['.a', '.c']);
});

test('parseCss drops --custom-property declarations', () => {
  const rules = parseCss('.a { --x: 1; color: red }');
  assert.equal(rules[0].props['--x'], undefined);
  assert.equal(rules[0].props.color, 'red');
});

// ── extractBodyChildren ─────────────────────────────────────────────────────

test('extractBodyChildren returns top-level <body> elements', () => {
  const html = '<body><div class="a"></div><span id="b"></span></body>';
  const kids = extractBodyChildren(html);
  assert.equal(kids.length, 2);
  assert.equal(kids[0].tag, 'div');
  assert.equal(kids[0].attrs.class, 'a');
  assert.equal(kids[1].tag, 'span');
  assert.equal(kids[1].attrs.id, 'b');
});

test('extractBodyChildren handles void elements', () => {
  const html = '<body><img src="x"><br></body>';
  const kids = extractBodyChildren(html);
  assert.equal(kids.length, 2);
  assert.equal(kids[0].tag, 'img');
  assert.equal(kids[1].tag, 'br');
});

test('extractBodyChildren falls back to whole document when no <body>', () => {
  // Some tiny WPT reftests omit explicit <body>; everything after the head
  // counts as body content. A bare <div> at root should still be extracted.
  const html = '<style>x</style><div class="t"></div>';
  const kids = extractBodyChildren(html);
  // Style block wraps a non-element token but our walker also picks up the
  // <style> element; that's fine — INSTRUCTION_TAGS filters it downstream.
  const tags = kids.map((k) => k.tag);
  assert.ok(tags.includes('div'));
});

test('extractBodyChildren handles nested same-name tags', () => {
  const html = '<body><div class="outer"><div>inner</div></div><div class="next"></div></body>';
  const kids = extractBodyChildren(html);
  assert.equal(kids.length, 2);
  assert.equal(kids[0].attrs.class, 'outer');
  assert.equal(kids[1].attrs.class, 'next');
});

// ── selectorMatches ─────────────────────────────────────────────────────────

test('selectorMatches: tag selector', () => {
  assert.equal(selectorMatches('div', 'div', {}), true);
  assert.equal(selectorMatches('div', 'span', {}), false);
});

test('selectorMatches: class selector', () => {
  assert.equal(selectorMatches('.a', 'div', { class: 'a' }), true);
  assert.equal(selectorMatches('.a', 'div', { class: 'b' }), false);
});

test('selectorMatches: id + class combo', () => {
  assert.equal(selectorMatches('div#x.y', 'div', { id: 'x', class: 'y z' }), true);
  assert.equal(selectorMatches('div#x.y', 'div', { id: 'x', class: 'z' }), false);
});

test('selectorMatches: descendant — applies last compound', () => {
  // We intentionally drop ancestor compounds — the orchestrator only
  // applies rules to top-level body children, so the rightmost selector
  // is what governs.
  assert.equal(selectorMatches('.parent .target', 'div', { class: 'target' }), true);
});

test('selectorMatches: rejects pseudo / attr / combinator selectors', () => {
  assert.equal(selectorMatches('a:hover', 'a', {}), false);
  assert.equal(selectorMatches('div + p', 'p', {}), false);
  assert.equal(selectorMatches('[data-x]', 'div', {}), false);
});

// ── propsForElement ─────────────────────────────────────────────────────────

test('propsForElement: matched rule props are merged', () => {
  const rules = [{ selector: '.t', props: { color: 'red', padding: '8px' } }];
  const { props } = propsForElement(rules, 'div', { class: 't' });
  assert.equal(props.color, 'red');
  assert.equal(props.padding, '8px');
});

test('propsForElement: inline style overrides matching rules', () => {
  const rules = [{ selector: '.t', props: { color: 'red' } }];
  const { props } = propsForElement(rules, 'div', { class: 't', style: 'color: blue' });
  assert.equal(props.color, 'blue');
});

test('propsForElement: counts matched rules', () => {
  const rules = [
    { selector: '.a', props: { color: 'red' } },
    { selector: '.a', props: { padding: '8px' } },
    { selector: '.b', props: { margin: '4px' } },
  ];
  const { matchedRules } = propsForElement(rules, 'div', { class: 'a' });
  assert.equal(matchedRules, 2);
});

// ── Bug 1: <p>/<h1..h3>/<noscript>/<script> are NOT pre-filtered ───────────
//
// pilot-001 / css-color/color-001: previously the extractor dropped every
// styled <p class="test"> on sight via INSTRUCTION_TAGS. Fixture builder
// now relies on the matchedRules===0 + empty-props branch to skip true
// instruction nodes; styled <p>s survive into the IR.

test('bug1: extractBodyChildren keeps <p>/<h1..h3>/<noscript> elements', () => {
  // Previously these were dropped by an INSTRUCTION_TAGS filter inside
  // extractBodyChildren or its callers. The walker itself returns them; the
  // fixture builder downstream is responsible for the empty-node fallback.
  const html = '<body><p class="test">x</p><h1>y</h1><noscript>z</noscript><script>q</script></body>';
  const kids = extractBodyChildren(html);
  const tags = kids.map((k) => k.tag);
  assert.deepEqual(tags, ['p', 'h1', 'noscript', 'script']);
});

test('bug1: a styled <p class="test"> retains its CSS rules via propsForElement', () => {
  // Mirror of css/css-color/color-001.html — the canonical WPT pattern that
  // motivated the Bug 1 fix.
  const rules = parseCss('.test { color: green }');
  const { props, matchedRules } = propsForElement(rules, 'p', { class: 'test' });
  assert.equal(matchedRules, 1);
  assert.equal(props.color, 'green');
});

// ── Bug 2: subtree recursion + descendant-combinator selector matching ─────
//
// pilot-001 / css-counter-styles/css3-counter-styles-101: selectors like
// `ol li {…}` or `.parent .child {…}` previously failed to apply because
// only top-level body children were walked. extractBodyTree + the new
// ancestors-aware selectorMatches close that gap.

test('bug2: extractBodyTree recurses into nested elements', () => {
  const html = '<body><div class="outer"><ol><li>a</li><li>b</li></ol></div></body>';
  const tree = extractBodyTree(html);
  const tags = tree.map((n) => n.tag);
  // div (depth 0), ol (depth 1), li, li (depth 2). All present.
  assert.deepEqual(tags, ['div', 'ol', 'li', 'li']);
  // li nodes carry their ancestor chain so `ol li` selectors can match.
  const li = tree[2];
  const ancestorTags = li.ancestors.map((a) => a.tag);
  assert.deepEqual(ancestorTags, ['div', 'ol']);
});

test('bug2: selectorMatches honours descendant combinators when ancestors are passed', () => {
  // `ol li` must match an <li> whose ancestor chain includes an <ol>.
  assert.equal(
    selectorMatches('ol li', 'li', {}, [{ tag: 'div', attrs: {} }, { tag: 'ol', attrs: {} }]),
    true,
  );
  // ...and must NOT match an <li> with no <ol> ancestor.
  assert.equal(
    selectorMatches('ol li', 'li', {}, [{ tag: 'div', attrs: {} }]),
    false,
  );
  // Without ancestors, falls back to the legacy "rightmost compound only"
  // behaviour (existing test pinned this) so old call sites stay green.
  assert.equal(selectorMatches('ol li', 'li', {}), true);
});

test('bug2: descendant chain follows document order', () => {
  // ancestors[] is ordered outermost-first (root → immediate parent) to
  // mirror how the recursive tree walker builds the chain.
  //
  // `.outer .inner` on a div.inner whose ancestors are [div.outer, span]:
  // matches because .outer appears somewhere up the chain.
  assert.equal(
    selectorMatches('.outer .inner', 'div', { class: 'inner' }, [
      { tag: 'div', attrs: { class: 'outer' } },
      { tag: 'span', attrs: {} },
    ]),
    true,
  );
  // Negative case: ancestor chain has the wrong tag — should fail.
  assert.equal(
    selectorMatches('.outer .inner', 'div', { class: 'inner' }, [
      { tag: 'span', attrs: {} },
    ]),
    false,
  );
  // Adjacent compounds in selector must match the chain in order:
  // `.a .b .c` against a chain [.a, .b] matched on .c → both ancestors
  // need to be consumed in order. .a (ancestor[0]) then .b (ancestor[1]).
  assert.equal(
    selectorMatches('.a .b .c', 'div', { class: 'c' }, [
      { tag: 'div', attrs: { class: 'a' } },
      { tag: 'div', attrs: { class: 'b' } },
    ]),
    true,
  );
  // Same compounds in reverse: .b is ancestor[0], .a is ancestor[1] —
  // the chain doesn't satisfy `.a .b` because .a comes AFTER .b.
  assert.equal(
    selectorMatches('.a .b .c', 'div', { class: 'c' }, [
      { tag: 'div', attrs: { class: 'b' } },
      { tag: 'div', attrs: { class: 'a' } },
    ]),
    false,
  );
});

test('bug2: propsForElement picks up `ol li` rule via ancestors chain', () => {
  // Mirror of css/css-counter-styles/arabic-indic/css3-counter-styles-101:
  // the rule `ol li { list-style-type: arabic-indic }` should land on every
  // <li> nested inside an <ol>.
  const rules = parseCss('ol li { list-style-type: arabic-indic } ol { padding-left: 8em }');
  const { props } = propsForElement(rules, 'li', {}, [
    { tag: 'div', attrs: { class: 'test' } },
    { tag: 'ol', attrs: {} },
  ]);
  assert.equal(props['list-style-type'], 'arabic-indic');
  // The bare `ol` rule must NOT bleed onto the <li>.
  assert.equal(props['padding-left'], undefined);
});

// ── Bug 3 + 4: synthetic body-root component for body-scope CSS ────────────
//
// pilot-001 / css-contain/contain-body-bg-001 +
// css-backgrounds/background-color-animation-in-body: rules scoped to
// `body { … }` or `html { … }` were silently dropped because no component
// represented the body itself. propsForBodyRoot picks them out so the
// fixture builder can emit a synthetic `__body` root component.

test('bug3+4: propsForBodyRoot collects body-scope rules', () => {
  const rules = parseCss('body { background: red; contain: layout } p { color: blue }');
  const { props, matchedRules } = propsForBodyRoot(rules);
  assert.equal(matchedRules, 1);
  assert.equal(props.background, 'red');
  assert.equal(props.contain, 'layout');
  // Non-root rules don't leak in.
  assert.equal(props.color, undefined);
});

test('bug3+4: propsForBodyRoot also matches html and * selectors', () => {
  // The CSS comma-list expansion in parseCss splits `html, body, p` into
  // three rules — html and body should both contribute, p should not.
  const rules = parseCss('html, body, p { margin: 0 } * { box-sizing: border-box }');
  const { props } = propsForBodyRoot(rules);
  assert.equal(props.margin, '0');
  assert.equal(props['box-sizing'], 'border-box');
});

test('bug3+4: propsForBodyRoot ignores descendant + pseudo + combinator selectors', () => {
  // `body p` is for the <p>, not the body root. `body:hover` is unsupported.
  // `body > div` uses an unsupported combinator.
  const rules = parseCss(
    'body p { color: red } body:hover { color: blue } body > div { color: green }'
  );
  const { matchedRules } = propsForBodyRoot(rules);
  assert.equal(matchedRules, 0);
});

// ── Bug 5: no phantom components from head-only elements ───────────────────
//
// pilot-001 / css-anchor-position/anchor-center-no-default: when there's no
// explicit <body>, the walker would otherwise emit one component per
// <title>/<meta>/<link>/<style> at the document root. extractBodyTree skips
// HEAD_ONLY_TAGS at every depth and unwraps an implicit <html>.

test('bug5: extractBodyTree skips head-only elements in no-<body> fallback', () => {
  // Mirror of anchor-center-no-default.html shape: title + link + style + div,
  // no <body>. Only the <div> should be emitted.
  const html =
    '<title>x</title>' +
    '<link rel="match" href="ref.html">' +
    '<style>.t { color: red }</style>' +
    '<div id="centered">hi</div>';
  const tree = extractBodyTree(html);
  const tags = tree.map((n) => n.tag);
  assert.deepEqual(tags, ['div']);
});

test('bug5: extractBodyTree unwraps implicit <html> wrapper', () => {
  // Many WPT tests open <html> but never close it and never open <body>.
  // The unwrap step pushes body content up to depth 0 so body-scope rules
  // (Bug 3) and child elements aren't double-nested.
  const html = '<!doctype html><html lang="en"><meta charset="utf-8"><div class="t">hi</div>';
  const tree = extractBodyTree(html);
  const tags = tree.map((n) => n.tag);
  // <meta> head-filtered, <html> unwrapped → only <div>.
  assert.deepEqual(tags, ['div']);
  // ancestors empty because div is at depth 0 after unwrap.
  assert.deepEqual(tree[0].ancestors, []);
});

test('bug5: extractBodyChildren still skips head-only tags in fallback mode', () => {
  // Back-compat angle: extractBodyChildren is the legacy export and its
  // existing fallback test ("falls back to whole document when no <body>")
  // doesn't break — but now <style>/<title>/<link> never make it into the
  // returned list at all (whereas before only INSTRUCTION_TAGS filtering at
  // the call site dropped them).
  const html = '<title>t</title><style>x</style><div class="t"></div>';
  const kids = extractBodyChildren(html);
  const tags = kids.map((k) => k.tag);
  assert.deepEqual(tags, ['div']);
});

// ── extractBodyTree: depth bounding ────────────────────────────────────────
//
// Default maxDepth=3 prevents pathological nesting from blowing up the IR.
// We don't need a regression test for performance, but we do need to make
// sure deeply nested elements still surface within the budget.

test('extractBodyTree respects maxDepth bound', () => {
  // 5 levels of nesting; default maxDepth=3 means we get a, b, c (depths
  // 0/1/2) but NOT d/e.
  const html = '<body><div class="a"><div class="b"><div class="c"><div class="d"><div class="e"></div></div></div></div></div></body>';
  const tree = extractBodyTree(html);
  const classes = tree.map((n) => n.attrs.class);
  assert.deepEqual(classes, ['a', 'b', 'c']);
});

// ── EXTFIX-A: nested children + own-text contract ──────────────────────────
//
// swarm-001 root-cause synthesis (see investigations/swarm-001/): the
// previous extractor flattened nested DOM into top-level siblings, which
// silently destroyed parent-child relationships needed for overflow:clip,
// position:absolute, mix-blend-mode, container queries and ~10 other test
// families to be meaningful (~800–1500 tests). It also dropped element
// text content, so colour / font / text-decor tests rendered empty boxes.
// extractBodyTreeNested + extractOwnText close both gaps.

test('extfixA: extractOwnText returns own text only, not descendants', () => {
  // <p>before <span>nested</span> after</p> — own text is "before  after"
  // (with the gap where the span was) collapsing to "before after".
  // The "nested" string belongs to the <span>, not the <p>.
  assert.equal(
    extractOwnText('before <span>nested</span> after'),
    'before after',
  );
});

test('extfixA: extractOwnText collapses whitespace + skips void elements', () => {
  // \n\t around text → collapsed; <br> is void → entirely skipped, not
  // its inner content (it has none anyway).
  assert.equal(
    extractOwnText('  hello\n\tworld  <br>  again  '),
    'hello world again',
  );
  // Element with only descendant text returns ''.
  assert.equal(
    extractOwnText('<span>only nested</span>'),
    '',
  );
  // Empty / undefined inputs return ''.
  assert.equal(extractOwnText(''), '');
  assert.equal(extractOwnText(undefined), '');
});

test('extfixA: extractBodyTreeNested produces a tree with children populated', () => {
  // Mirror of css/css-overflow/clip-001.html: 3-level nest where the
  // child relationship is the WHOLE point of the test.
  const html =
    '<body>' +
    '<div id="container">' +
    '<div id="target">' +
    '<div id="fill"></div>' +
    '</div>' +
    '</div>' +
    '</body>';
  const tree = extractBodyTreeNested(html);
  // Top-level: one element (#container).
  assert.equal(tree.length, 1);
  assert.equal(tree[0].attrs.id, 'container');
  // #container has one child: #target.
  assert.equal(tree[0].children.length, 1);
  assert.equal(tree[0].children[0].attrs.id, 'target');
  // #target has one child: #fill (depth 2).
  assert.equal(tree[0].children[0].children.length, 1);
  assert.equal(tree[0].children[0].children[0].attrs.id, 'fill');
  // #fill has no children.
  assert.deepEqual(tree[0].children[0].children[0].children, []);
});

test('extfixA: extractBodyTreeNested attaches ownText per element', () => {
  // Mirror of css/css-color/color-001.html: <p class="test"> with text.
  // The text is OWN text of the <p>, not of any descendant.
  const html = '<body><p class="test">Test passes if this text is green</p></body>';
  const tree = extractBodyTreeNested(html);
  assert.equal(tree.length, 1);
  assert.equal(tree[0].tag, 'p');
  assert.equal(tree[0].ownText, 'Test passes if this text is green');
  assert.deepEqual(tree[0].children, []);
});

test('extfixA: extractBodyTreeNested separates parent vs child text', () => {
  // <div>parent text<span>child text</span> more parent</div>
  // Own text of <div> is "parent text  more parent" → "parent text more parent";
  // Own text of <span> is "child text".
  const html = '<body><div>parent text<span>child text</span> more parent</div></body>';
  const tree = extractBodyTreeNested(html);
  assert.equal(tree[0].ownText, 'parent text more parent');
  assert.equal(tree[0].children.length, 1);
  assert.equal(tree[0].children[0].tag, 'span');
  assert.equal(tree[0].children[0].ownText, 'child text');
});

test('extfixA: extractBodyTreeNested honours maxDepth=5 cap', () => {
  // 7 levels deep; with maxDepth=5 the deepest populated node is at
  // depth 4 (its children stay []), confirming the cap.
  const html =
    '<body>' +
    '<div class="d0"><div class="d1"><div class="d2"><div class="d3">' +
    '<div class="d4"><div class="d5"><div class="d6"></div></div></div>' +
    '</div></div></div></div>' +
    '</body>';
  const tree = extractBodyTreeNested(html);
  // Walk depth chain.
  let cur = tree[0];
  const seen = [];
  while (cur) {
    seen.push(cur.attrs.class);
    cur = cur.children[0];
  }
  // Expect d0 d1 d2 d3 d4 — five levels exactly. d5/d6 are below the cap.
  assert.deepEqual(seen, ['d0', 'd1', 'd2', 'd3', 'd4']);
});

test('extfixA: extractBodyTreeNested ancestors chain mirrors extractBodyTree', () => {
  // Same html as the bug2 test — ensure the new walker produces the same
  // ancestors-by-document-order chain so descendant selectors keep matching.
  const html = '<body><div class="outer"><ol><li>a</li><li>b</li></ol></div></body>';
  const tree = extractBodyTreeNested(html);
  // Drill down to the first <li>.
  const div = tree[0];
  const ol = div.children[0];
  const li = ol.children[0];
  assert.equal(li.tag, 'li');
  // Ancestors must be [div, ol] — outermost first, immediate parent last —
  // matching extractBodyTree's contract (consumed by selectorMatches).
  const ancestorTags = li.ancestors.map((a) => a.tag);
  assert.deepEqual(ancestorTags, ['div', 'ol']);
});

test('extfixA: extractBodyTreeNested skips head-only tags at every depth', () => {
  // Bug 5 invariant carries over: <style> / <meta> nested inside a div
  // never become components. Otherwise an inline <style> in body content
  // would be emitted as a phantom child.
  const html =
    '<body><div class="parent"><style>.x{color:red}</style><span>kept</span></div></body>';
  const tree = extractBodyTreeNested(html);
  assert.equal(tree[0].children.length, 1);
  assert.equal(tree[0].children[0].tag, 'span');
});

test('extfixA: buildComponents nests descendants under parent.children (not as siblings)', () => {
  // Mirror of css/css-grid/abspos/grid-abspos-staticpos-align-items-center.html.
  // Pre-EXTFIX-A this would emit grid + abspos as TWO top-level siblings
  // (`grid__0` + `grid__1`), losing the abspos's containing-block
  // relationship. Post-EXTFIX-A the abspos lives at grid.children[0].
  const html =
    '<body>' +
    '<div class="grid">' +
    '<div class="abspos"></div>' +
    '</div>' +
    '</body>';
  const rules = parseCss(
    '.grid { display: grid; width: 100px; height: 100px; align-items: center }' +
    '.abspos { position: absolute; width: 50px; height: 50px; background: green }',
  );
  const { components } = buildComponents(html, rules, 'grid');
  // Exactly ONE top-level component (the grid). No `__1` sibling.
  const keys = Object.keys(components);
  assert.deepEqual(keys, ['grid__0']);
  // The grid has the abspos as its single child. Children stored as a
  // map (id → child) per the Kotlin parser's expectation at
  // CssParsing.kt:96 — `obj["children"]?.jsonObject?.mapValues { ... }`.
  const parent = components['grid__0'];
  assert.equal(parent.properties.display, 'grid');
  assert.ok(parent.children && typeof parent.children === 'object' && !Array.isArray(parent.children),
    'parent.children must be a map (id → child), not an array');
  const childKeys = Object.keys(parent.children);
  assert.equal(childKeys.length, 1);
  // Child carries its own id (matching IRComponent.id contract) and the
  // abspos properties.
  const child = parent.children[childKeys[0]];
  assert.equal(child.id, 'grid__0__0');
  assert.equal(child.properties.position, 'absolute');
  assert.equal(child.properties.background, 'green');
});

test('extfixA: buildComponents emits _text on elements with own text', () => {
  // Mirror of css/css-color/color-001.html — the canonical text-styling
  // case that motivated EXTFIX-A part 2.
  const html =
    '<body><p class="test">Test passes if this text is green</p></body>';
  const rules = parseCss('.test { color: green }');
  const { components } = buildComponents(html, rules, 'color-001');
  const cmp = components['color-001__0'];
  assert.equal(cmp.properties.color, 'green');
  assert.equal(cmp._text, 'Test passes if this text is green');
  // No children (the <p> has no element descendants).
  assert.equal(cmp.children, undefined);
});

test('extfixA: buildComponents omits _text and children when empty (back-compat)', () => {
  // Single empty styled <div> — fixture must stay byte-identical to the
  // pre-EXTFIX-A shape (no `_text` field, no `children` field). This is
  // the ~70% case in the existing fixtures/wpt/ corpus and we don't want
  // to balloon the diff.
  const html = '<body><div class="t"></div></body>';
  const rules = parseCss('.t { color: red }');
  const { components } = buildComponents(html, rules, 'stem');
  const cmp = components['stem__0'];
  assert.deepEqual(Object.keys(cmp), ['properties']);
  assert.equal(cmp.properties.color, 'red');
});

// ── wave-11 fix 2: leading anonymous body text ──────────────────────────────
//
// css-grid/abspos/descendant-static-position-001..004: bare instruction
// prose ("There should be no red:") is a direct <body> child TEXT node.
// Browsers give it an anonymous block box (CSS 2.1 §9.2.1.1) one line box
// tall before the first element; the walker only emits elements, so every
// platform rendered ~18px higher than the browser-ref. The extractor now
// emits the leading run as a `<idPrefix>__text` component on the same
// `_text` channel the per-element ownText path uses.

test('wave11: extractLeadingBodyText returns the bare prose before the first element', () => {
  // Mirror of descendant-static-position-001.html's body shape.
  const html =
    '<body>\nThere should be no red:\n\n<div class="grid"><div></div></div></body>';
  assert.equal(extractLeadingBodyText(html), 'There should be no red:');
});

test('wave11: extractLeadingBodyText is empty when body opens with an element', () => {
  // The ~common case — fixtures must stay byte-identical (no __text key).
  assert.equal(extractLeadingBodyText('<body><div class="t">x</div>tail</body>'), '');
  // Whitespace-only leading runs are NOT anonymous boxes (collapsed away).
  assert.equal(extractLeadingBodyText('<body>\n  \n<div></div></body>'), '');
});

test('wave11: extractLeadingBodyText skips head scaffolding in no-<body> fallback', () => {
  // Body-less minimal WPT shape: head tags precede the prose; <style>
  // content must NOT leak into the text, but the prose after it (still
  // before the first renderable element) is the leading anonymous run.
  const html =
    '<link rel="match" href="r.html"><style>.t { color: red }</style>\n' +
    'Instruction text\n<p class="t">subject</p>';
  assert.equal(extractLeadingBodyText(html), 'Instruction text');
});

test('wave11: buildComponents emits the leading text as a __text component before __0', () => {
  const html =
    '<body>There should be no red:\n<div class="grid"></div></body>';
  const rules = parseCss('.grid { width: 20px }');
  const { components } = buildComponents(html, rules, 'dsp-001');
  // The leading-text component rides the same `_text` channel as ownText,
  // with an EMPTY properties bag (prose lays out at renderer defaults).
  assert.deepEqual(components['dsp-001__text'], { properties: {}, _text: 'There should be no red:' });
  // Document order: __text precedes the element siblings in map insertion
  // order (which is what every renderer iterates).
  const keys = Object.keys(components);
  assert.ok(keys.indexOf('dsp-001__text') < keys.indexOf('dsp-001__0'),
    `__text must precede __0; got ${keys}`);
  // The element sibling is untouched.
  assert.equal(components['dsp-001__0'].properties.width, '20px');
});

test('wave11: text-only body yields __text instead of the 100x100 placeholder', () => {
  // Degenerate branch: with real prose on the canvas the phantom
  // placeholder box must NOT be emitted alongside it.
  const { components } = buildComponents('<body>just prose</body>', [], 'stem');
  assert.deepEqual(components, { stem__text: { properties: {}, _text: 'just prose' } });
});

test('wave11: element-first body emits no __text key (byte-identical fixtures)', () => {
  const { components } = buildComponents('<body><div class="t"></div></body>',
    parseCss('.t { color: red }'), 'stem');
  assert.equal(components['stem__text'], undefined);
});

// ── Bug 1 (F-EXTRACTOR retry): `_tag` field emission ───────────────────────
//
// swarm-002 / css-counter-styles/css3-counter-styles-101: components had no
// element-identity field. F-RENDERER reads `_tag` (lowercase HTML tag name)
// to branch on real HTML semantics (<ol>/<li> trigger native list marker
// generation, <p> gets paragraph rhythm, etc.). We emit `_tag` for every
// non-generic tag; <div>/<span> stay tag-less to keep visual-test-style
// fixtures byte-identical.

test('bug1-fextractor: buildComponents emits _tag for non-generic elements', () => {
  // <ol> + <li> are the canonical motivating case — list rendering hinges
  // on them. Both must carry `_tag`.
  const html =
    '<body>' +
    '<ol class="t"><li>a</li><li>b</li></ol>' +
    '</body>';
  const { components } = buildComponents(html, parseCss(''), 'list');
  const ol = components['list__0'];
  assert.equal(ol._tag, 'ol');
  // First child of the <ol> is the first <li>.
  const firstChildKey = Object.keys(ol.children)[0];
  assert.equal(ol.children[firstChildKey]._tag, 'li');
});

test('bug1-fextractor: buildComponents omits _tag for generic <div>/<span>', () => {
  // A bare <div class="t"> mirrors the shape of every visual-test fixture
  // — adding `_tag: "div"` here would balloon hundreds of existing fixtures
  // for zero renderer benefit (the renderer already defaults to div/Box).
  const html = '<body><div class="t"></div><span class="u">x</span></body>';
  const { components } = buildComponents(html, parseCss(''), 'stem');
  assert.equal(components['stem__0']._tag, undefined);
  assert.equal(components['stem__1']._tag, undefined);
});

test('bug1-fextractor: buildComponents emits _tag for the canonical text-styling tags', () => {
  // <p>, headings, <table>, <td>, <th>, <a>, <strong> etc. all carry
  // semantic weight in the renderer. Spot-check the most-used ones.
  const html =
    '<body>' +
    '<p>para</p>' +
    '<h1>title</h1>' +
    '<table><tr><td>cell</td></tr></table>' +
    '<a href="x">link</a>' +
    '</body>';
  const { components } = buildComponents(html, parseCss(''), 'stem');
  assert.equal(components['stem__0']._tag, 'p');
  assert.equal(components['stem__1']._tag, 'h1');
  assert.equal(components['stem__2']._tag, 'table');
  // Nested <td> deep inside <table>.
  const table = components['stem__2'];
  const trKey = Object.keys(table.children)[0];
  const tr = table.children[trKey];
  const tdKey = Object.keys(tr.children)[0];
  assert.equal(tr.children[tdKey]._tag, 'td');
  assert.equal(components['stem__3']._tag, 'a');
});

// ── Bug 2a (F-EXTRACTOR retry): structural pseudo-classes ──────────────────
//
// swarm-002 / selectors__child-indexed-no-parent: the prior compoundMatches
// rejected every compound containing `:` or `[`, silently dropping rules
// like `:root:first-child #a`. We now allow-list structural pseudos and
// evaluate them against position metadata (with Selectors-4 §6.4.1 carve-
// out for the parentless root).

test('bug2a: selectorMatches honours :root via synthetic root ancestor', () => {
  // Direct `selectorMatches` call: when the leftmost compound is `:root`,
  // the chain must include a root sentinel. propsForElement injects this
  // automatically; the unit test wires it up explicitly here.
  const rootAncestor = { tag: ':root', attrs: {}, pos: { isRoot: true } };
  assert.equal(
    selectorMatches(':root #a', 'p', { id: 'a' }, [rootAncestor]),
    true,
  );
  // Negative: bare `:root` with no synthetic root in the chain → no match.
  assert.equal(
    selectorMatches(':root #a', 'p', { id: 'a' }, []),
    false,
  );
});

test('bug2a: propsForElement matches :root:<child-indexed> via injected root', () => {
  // Mirror of selectors/child-indexed-no-parent.html: 8 rules of the form
  // `:root:<pseudo> #X { color: green }` should all land on <p id="X">
  // even though the test caller doesn't pass any explicit root ancestor.
  const rules = parseCss(
    ':root:first-child #a { color: green }' +
    ':root:nth-child(n) #b { color: green }' +
    ':root:last-of-type #c { color: green }' +
    ':root:nth-last-child(2) #d { color: red }', // intentional NOT-match
  );
  // #a — :first-child true on root → match.
  assert.equal(
    propsForElement(rules, 'p', { id: 'a' }, []).props.color,
    'green',
  );
  // #b — :nth-child(n) true (n=1 → 1) → match.
  assert.equal(
    propsForElement(rules, 'p', { id: 'b' }, []).props.color,
    'green',
  );
  // #c — :last-of-type true on root → match.
  assert.equal(
    propsForElement(rules, 'p', { id: 'c' }, []).props.color,
    'green',
  );
  // #d — :nth-last-child(2) NOT true (carve-out: only matches when An+B = 1).
  // The colour from the intentional-NOT-match rule must NOT land.
  assert.equal(
    propsForElement(rules, 'p', { id: 'd' }, []).props.color,
    undefined,
  );
});

test('bug2a: selectorMatches evaluates :nth-child(An+B) per sibling index', () => {
  // Two children of a parent; check `:nth-child(2)` matches only the
  // second, not the first. `pos` carries the 0-based sibling index.
  const ancestors = [{ tag: 'div', attrs: {} }];
  const posFirst  = { sibIndex: 0, sibCount: 2, sibTypeIndex: 0, sibTypeCount: 2 };
  const posSecond = { sibIndex: 1, sibCount: 2, sibTypeIndex: 1, sibTypeCount: 2 };
  assert.equal(
    selectorMatches('div :nth-child(2)', 'p', {}, ancestors, posFirst),
    false,
  );
  assert.equal(
    selectorMatches('div :nth-child(2)', 'p', {}, ancestors, posSecond),
    true,
  );
  // `:nth-child(odd)` matches the first but not the second.
  assert.equal(
    selectorMatches(':nth-child(odd)', 'p', {}, ancestors, posFirst),
    true,
  );
  assert.equal(
    selectorMatches(':nth-child(odd)', 'p', {}, ancestors, posSecond),
    false,
  );
});

// ── Bug 2b: HTML5 auto-closing tags ────────────────────────────────────────

test('bug2b: walkChildren auto-closes <p> at the next sibling block opener', () => {
  // Two consecutive `<p>` without closers — the first should auto-close
  // when the second `<p>` opens. Each `<p>` keeps its own text.
  const html = '<p>first</p><p>second';
  const kids = extractBodyChildren(html);
  assert.equal(kids.length, 2);
  assert.equal(kids[0].tag, 'p');
  assert.equal(kids[1].tag, 'p');
});

test('bug2b: walkChildren preserves text inside implicit-close <p> elements', () => {
  // Mirror of selectors/child-indexed-no-parent.html shape: a string of
  // unclosed `<p id="X">Should be green` lines. Each must surface as a
  // distinct component carrying its text.
  const html =
    '<body>' +
    '<p id="a">first text\n' +
    '<p id="b">second text\n' +
    '<p id="c">third text\n' +
    '</body>';
  const tree = extractBodyTreeNested(html);
  assert.equal(tree.length, 3);
  assert.equal(tree[0].ownText, 'first text');
  assert.equal(tree[1].ownText, 'second text');
  assert.equal(tree[2].ownText, 'third text');
});

test('bug2b: walkChildren auto-closes <li> at the next <li>', () => {
  // Authoring shorthand allowed by HTML5: `<ol><li>a<li>b<li>c</ol>`.
  // Each <li> must surface as its own component with its own text.
  const html = '<body><ol><li>a<li>b<li>c</ol></body>';
  const tree = extractBodyTreeNested(html);
  // top level: <ol>; its children: three <li>s.
  assert.equal(tree.length, 1);
  assert.equal(tree[0].tag, 'ol');
  assert.equal(tree[0].children.length, 3);
  assert.deepEqual(
    tree[0].children.map((c) => c.tag),
    ['li', 'li', 'li'],
  );
  assert.deepEqual(
    tree[0].children.map((c) => c.ownText),
    ['a', 'b', 'c'],
  );
});

// ── Bug 2c: propsForBodyRoot accepts :root ─────────────────────────────────

test('bug2c: propsForBodyRoot accepts bare :root', () => {
  // `:root { color: green }` is the synonym for `html { color: green }`
  // most modern authors use. It must land on the synthetic body root.
  const rules = parseCss(':root { color: green; background: white }');
  const { props, matchedRules } = propsForBodyRoot(rules);
  assert.equal(matchedRules, 1);
  assert.equal(props.color, 'green');
  assert.equal(props.background, 'white');
});

test('bug2c: propsForBodyRoot accepts :root with the always-true pseudo carve-out', () => {
  // `:root:first-child` is always-true on the parentless root.
  // `:root:nth-last-child(2)` should NOT land (Selectors-4 §6.4.1 says
  // An+B only matches when it yields 1; 2 doesn't).
  const rules = parseCss(
    ':root:first-child { color: green } ' +
    ':root:nth-last-child(2) { color: red }',
  );
  const { props } = propsForBodyRoot(rules);
  assert.equal(props.color, 'green');
});

// ── Bug 3: U+3000 IDEOGRAPHIC SPACE preserved in own-text ──────────────────
//
// swarm-002 / css-text/hanging-punctuation-first-002: extractOwnText used
// the JS `\s` whitespace class which matches U+3000 and silently stripped
// the leading ideographic space from `<div class=test>　↓</div>`. The
// hanging-punctuation test signal vanished. We now only collapse the
// ASCII subset per CSS Text §4.1.

test('bug3: extractOwnText preserves leading U+3000 IDEOGRAPHIC SPACE', () => {
  // Direct mirror of the failing fixture's text.
  assert.equal(extractOwnText('　↓'), '　↓');
  // Same character somewhere in the middle stays put too.
  assert.equal(extractOwnText('a　b'), 'a　b');
});

test('bug3: extractOwnText preserves U+00A0 NO-BREAK SPACE and other non-ASCII whitespace', () => {
  // CSS Text §4.1 limits collapsible whitespace to space/tab/LF/CR/FF.
  // Anything else — NBSP, IDEOGRAPHIC SPACE, EN SPACE, EM SPACE,
  // ZERO WIDTH SPACE — must survive verbatim.
  assert.equal(extractOwnText('a b'),   'a b');   // NBSP
  assert.equal(extractOwnText('a b'),   'a b');   // EN SPACE
  assert.equal(extractOwnText('a b'),   'a b');   // EM SPACE
});

test('bug3: extractOwnText still collapses ASCII whitespace runs', () => {
  // Back-compat check — the existing collapse behaviour for plain ASCII
  // whitespace must remain. Mixed runs collapse correctly too.
  assert.equal(extractOwnText('a  b'),        'a b');
  assert.equal(extractOwnText('a\t\nb'),      'a b');
  assert.equal(extractOwnText('  hi  '),      'hi');
  // U+3000 sandwiched between ASCII spaces stays exactly between
  // collapsed-single-space ASCII runs.
  assert.equal(extractOwnText('  a　b  '),   'a　b');
});

test('extfixA: buildComponents propagates lossy markers down children', () => {
  // A child carrying `em`/`%`/`var()` values must surface the lossy reason
  // on the OVERALL fixture (lossyOverall) so the dashboard's lossy lane
  // counts the whole tree, not just top-level. Also pin that the per-child
  // _lossy/_lossyReasons fields are set on the offending child only —
  // the parent's properties don't get tagged unless they themselves use
  // a lossy unit.
  const html =
    '<body><div class="parent"><span class="kid">x</span></div></body>';
  const rules = parseCss(
    '.parent { color: red } ' +
    '.kid { font-size: 1.5em; width: 50% }',
  );
  const { components, lossyOverall, lossyReasons } =
    buildComponents(html, rules, 'stem');
  assert.equal(lossyOverall, true);
  // Both em/rem and percentage must be reported.
  assert.ok(lossyReasons.includes('em/rem'));
  assert.ok(lossyReasons.includes('percentage'));
  // Parent component is NOT lossy (only `color: red`).
  const parent = components['stem__0'];
  assert.equal(parent._lossy, undefined);
  // Child IS lossy. Children are stored as a map keyed by id.
  const kidKey = Object.keys(parent.children)[0];
  const kid = parent.children[kidKey];
  assert.equal(kid._lossy, true);
  assert.ok(kid._lossyReasons.includes('em/rem'));
  assert.ok(kid._lossyReasons.includes('percentage'));
});

// ── swarm-003 Bug 1: ::before / ::after / ::marker pseudo-element rules ───
//
// Three convergent investigations (swarm-001/002/003) traced ~150-200
// dropped tests to parseCompound rejecting every selector containing `::`.
// css-lists/counter-001 (::before+counter), css-counter-styles/* (::marker),
// css-pseudo/before-* (::before+content) all need the rule's declarations
// to land in a per-pseudo-element bucket on the host component so the
// sister F-G-RENDERER can synthesise an inline span with the declared
// `content`. We route them into `cmp._pseudo.<name>.properties`.

test('swarm003-bug1: buildComponents emits _pseudo.before with content for ::before rule', () => {
  // Mirror of css/css-pseudo/before-preceding-whitespace-dynamic.html.
  // The single rule `div::before { content: "two" }` should land on the
  // <div>'s `_pseudo.before.properties.content` slot, NOT on the host's
  // flat properties (where CSS GC L3 §3.2 makes `content` a no-op).
  const html = '<body><div>words</div></body>';
  const rules = parseCss('div::before { content: "two" }');
  const { components } = buildComponents(html, rules, 'pe');
  const div = components['pe__0'];
  assert.ok(div._pseudo, 'div component must carry _pseudo bucket');
  assert.ok(div._pseudo.before, '_pseudo.before must be populated');
  assert.equal(div._pseudo.before.properties.content, '"two"');
  // The host's flat properties must NOT contain `content` — that would
  // mis-apply the declaration to the host's box per CSS GC L3 §3.2.
  assert.equal(div.properties.content, undefined);
});

test('swarm003-bug1: buildComponents emits _pseudo.after when ::after rule matches via descendant chain', () => {
  // Mirror of css/css-lists/counter-001.html: `#test span::before { content: counter(c, ...) }`
  // tests descendant matching with a ::before suffix. We use ::after here
  // to vary the variant. The host span lives at children[0] of the #test div.
  const html =
    '<body>' +
    '<div id="test"><span></span><span></span></div>' +
    '</body>';
  const rules = parseCss('#test span::after { content: "x" }');
  const { components } = buildComponents(html, rules, 'pe');
  const container = components['pe__0'];
  const span0Key = Object.keys(container.children)[0];
  const span0 = container.children[span0Key];
  assert.ok(span0._pseudo, 'span must carry _pseudo bucket');
  assert.equal(span0._pseudo.after.properties.content, '"x"');
});

test('swarm003-bug1: buildComponents emits _pseudo.marker with list-style declarations', () => {
  // ::marker is the list-marker pseudo-element. The renderer turns this
  // into a leading inline-block on the host's first line. Used by
  // css-counter-styles/* tests that style the auto-generated bullet.
  const html = '<body><ol><li>item</li></ol></body>';
  const rules = parseCss('li::marker { content: "→ "; color: red }');
  const { components } = buildComponents(html, rules, 'pe');
  const ol = components['pe__0'];
  const liKey = Object.keys(ol.children)[0];
  const li = ol.children[liKey];
  assert.ok(li._pseudo, 'li must carry _pseudo bucket');
  assert.equal(li._pseudo.marker.properties.content, '"→ "');
  assert.equal(li._pseudo.marker.properties.color, 'red');
  // li's own properties (the text 'item') get no marker leak.
  assert.equal(li.properties.color, undefined);
});

test('swarm003-bug1: buildComponents omits _pseudo for components with no matching pseudo-rules (back-compat)', () => {
  // Byte-identical to pre-swarm-003 fixtures: a styled <div> with no
  // ::pseudo selector anywhere in the stylesheet must NOT grow a
  // `_pseudo` field. The ~99% case in the existing fixtures/wpt corpus.
  const html = '<body><div class="t"></div></body>';
  const rules = parseCss('.t { color: red }');
  const { components } = buildComponents(html, rules, 'stem');
  const cmp = components['stem__0'];
  assert.equal(cmp._pseudo, undefined);
  assert.deepEqual(Object.keys(cmp), ['properties']);
});

test('swarm003-bug1: parseCompound still rejects unsupported pseudo-elements like ::first-line', () => {
  // Per the per-property contract, unsupported pseudo-elements stay
  // unsupported (logged as TODOs in code). The rule must be dropped
  // honestly rather than mis-attached to the host or to a fake bucket.
  // We assert via selectorMatchesPseudoElement returning null.
  assert.equal(
    selectorMatchesPseudoElement('p::first-line', 'p', {}),
    null,
  );
  assert.equal(
    selectorMatchesPseudoElement('p::selection', 'p', {}),
    null,
  );
});

// ── swarm-003 Bug 2: :nth-child(An+B of S) — Selectors-4 extended form ────
//
// selectors__nth-child-of-pseudo-class motivated this. The legacy evalAnB
// rejected anything containing the keyword `of`; splitAnBOfSelector now
// splits the head from the suffix and evalPseudo renumbers among the
// siblings matching the selector list. ~30 selectors-4 corpus tests
// blocked on this; striped-table layouts (`tr:nth-child(odd of :not([hidden]))`)
// are the canonical motivating use case in Selectors 4.

test('swarm003-bug2: splitAnBOfSelector splits An+B head from of-selector tail', () => {
  // Bare An+B form — no `of` clause; the head equals the input.
  assert.deepEqual(splitAnBOfSelector('2n+1'), { anb: '2n+1', ofSelectors: null });
  assert.deepEqual(splitAnBOfSelector('odd'), { anb: 'odd', ofSelectors: null });
  // `An+B of S` — head and tail captured separately.
  assert.deepEqual(
    splitAnBOfSelector('odd of :defined'),
    { anb: 'odd', ofSelectors: [':defined'] },
  );
  assert.deepEqual(
    splitAnBOfSelector('2n+1 of .item'),
    { anb: '2n+1', ofSelectors: ['.item'] },
  );
  // Comma-separated selector list in the tail.
  assert.deepEqual(
    splitAnBOfSelector('odd of .a, .b'),
    { anb: 'odd', ofSelectors: ['.a', '.b'] },
  );
});

test('swarm003-bug2: nth-child(odd of :defined) matches odd-indexed :defined siblings', () => {
  // Mirror of the failing WPT shape css/selectors/invalidation/nth-child-of-pseudo-class.html:
  //   <div>
  //     <not-defined> <not-defined>     // skipped — not :defined
  //     <p#1> <my-element> <p#2> <p#3> <p#4>
  //     <not-defined>                    // skipped
  //   </div>
  // With customElements.define('my-element', …) → the :defined-matching
  // siblings (in document order, 1-based) are:
  //   filter-idx 1=p#1   2=my-element   3=p#2   4=p#3   5=p#4
  // `odd` picks filter indices {1, 3, 5} → host must also be `p`:
  //   - p#1 (filter 1) → MATCH (green)
  //   - p#2 (filter 3) → MATCH (green)
  //   - p#4 (filter 5) → MATCH (green)
  //   - p#3 (filter 4) → no match
  // Three of four <p>s painted green; the my-element + the not-defineds
  // never match because the host must be a <p>.
  const html =
    '<body>' +
    '<div>' +
    '<not-defined>x</not-defined>' +
    '<not-defined>x</not-defined>' +
    '<p>1</p>' +
    '<my-element>m</my-element>' +
    '<p>2</p>' +
    '<p>3</p>' +
    '<p>4</p>' +
    '<not-defined>x</not-defined>' +
    '</div>' +
    '</body>' +
    '<script>customElements.define("my-element", class extends HTMLElement{});</script>';
  const rules = parseCss('p:nth-child(odd of :defined) { color: green }');
  const { components } = buildComponents(html, rules, 'sel');
  const div = components['sel__0'];
  const kids = Object.values(div.children);
  // kids: 0/1=not-defined, 2=p#1, 3=my-element, 4=p#2, 5=p#3, 6=p#4, 7=not-defined.
  assert.equal(kids[2].properties.color, 'green', 'p#1 (filter idx 1) should be green');
  assert.equal(kids[4].properties.color, 'green', 'p#2 (filter idx 3) should be green');
  assert.equal(kids[5].properties.color, undefined, 'p#3 (filter idx 4) should NOT be green');
  assert.equal(kids[6].properties.color, 'green', 'p#4 (filter idx 5) should be green');
});

// ── swarm-003 Bug 3: :defined pseudo-class ────────────────────────────────

test('swarm003-bug3: collectDefinedTags harvests customElements.define names from script', () => {
  // Simple registration in an inline <script>.
  const html =
    '<script>customElements.define("my-element", class extends HTMLElement{});</script>' +
    '<script>customElements.define(\'other-tag\', class extends HTMLElement{});</script>';
  const tags = collectDefinedTags(html);
  assert.ok(tags.has('my-element'));
  assert.ok(tags.has('other-tag'));
});

test('swarm003-bug3: :defined is true for built-in HTML elements always', () => {
  // No script blocks, so definedTags is empty — but `<p>` is built-in
  // (no hyphen in tag name per HTML §4.13.1) so :defined still answers
  // true for it. We exercise via propsForElement so the full ctx flow
  // (defined-tag set assembled by buildComponents) is in play.
  const html = '<body><p>x</p><not-defined>y</not-defined></body>';
  const rules = parseCss(':defined { color: blue }');
  const { components } = buildComponents(html, rules, 'def');
  // <p> is built-in → :defined → color blue.
  assert.equal(components['def__0'].properties.color, 'blue');
  // <not-defined> has a hyphen and no registration → :defined false →
  // no color landed (or it stayed `undefined`).
  assert.equal(components['def__1'].properties.color, undefined);
});

// ── wave-8: child combinator (`A > B`) ─────────────────────────────────────
//
// Regression source: the six css-flexbox/abspos/abspos-autopos-* WPT tests
// style their subject via `.flex > div { position:absolute; … }` and
// flex-abspos-staticpos-fallback-justify-content-001 uses `.container > *`.
// The prior blanket `/[>+~]/` reject dropped these rules WHOLESALE, so the
// fixtures shipped placeholder children with no styles.

test('wave8: splitSelectorChain tokenises descendant + child chains', () => {
  // Spaced child combinator.
  assert.deepEqual(splitSelectorChain('.flex > div'),
    { compounds: ['.flex', 'div'], combinators: ['>'] });
  // No-space form is equally valid CSS.
  assert.deepEqual(splitSelectorChain('.flex>div'),
    { compounds: ['.flex', 'div'], combinators: ['>'] });
  // Pure descendant chains keep the ' ' combinator marker.
  assert.deepEqual(splitSelectorChain('ol li'),
    { compounds: ['ol', 'li'], combinators: [' '] });
  // Mixed chain: descendant then child.
  assert.deepEqual(splitSelectorChain('.a .b > .c'),
    { compounds: ['.a', '.b', '.c'], combinators: [' ', '>'] });
  // Three-compound child chain.
  assert.deepEqual(splitSelectorChain('a > b > c'),
    { compounds: ['a', 'b', 'c'], combinators: ['>', '>'] });
});

test('wave8: splitSelectorChain protects functional-pseudo arguments', () => {
  // Whitespace inside `:nth-child(2n + 1)` parens must not split, mirroring
  // splitCompounds' paren-awareness (swarm-003 Bug 2).
  assert.deepEqual(splitSelectorChain('ul :nth-child(2n + 1)'),
    { compounds: ['ul', ':nth-child(2n + 1)'], combinators: [' '] });
});

test('wave8: splitSelectorChain rejects sibling combinators and malformed chains', () => {
  assert.equal(splitSelectorChain('div + p'), null);   // next-sibling unsupported
  assert.equal(splitSelectorChain('div ~ p'), null);   // subsequent-sibling unsupported
  assert.equal(splitSelectorChain('> div'), null);     // leading child — malformed
  assert.equal(splitSelectorChain('div >'), null);     // trailing child — malformed
  assert.equal(splitSelectorChain('a >> b'), null);    // double child — malformed
});

test('wave8: child combinator matches only the IMMEDIATE parent', () => {
  // Ancestors are in document order: outermost first, immediate parent LAST.
  const flexParent = [{ tag: 'div', attrs: { class: 'flex' } }];
  // `.flex > div` matches a div whose immediate parent is .flex.
  assert.equal(selectorMatches('.flex > div', 'div', {}, flexParent), true);
  // Grandchild: immediate parent is a plain div, .flex is one level up —
  // the child combinator must NOT match (descendant `. flex div` would).
  const grandchild = [
    { tag: 'div', attrs: { class: 'flex' } },
    { tag: 'div', attrs: {} },
  ];
  assert.equal(selectorMatches('.flex > div', 'div', {}, grandchild), false);
  assert.equal(selectorMatches('.flex div', 'div', {}, grandchild), true);
});

test('wave8: `.container > *` matches any child of .container (the fallback-justify shape)', () => {
  const chain = [
    { tag: 'div', attrs: { class: 'big' } },
    { tag: 'div', attrs: { class: 'container' } },
  ];
  assert.equal(selectorMatches('.container > *', 'div', {}, chain), true);
  // `.big > .container` from the same test also holds for the container itself.
  assert.equal(selectorMatches('.big > .container', 'div', { class: 'container' },
    [{ tag: 'div', attrs: { class: 'big' } }]), true);
});

test('wave8: chained child combinators consume ancestors right-to-left', () => {
  const chain = [
    { tag: 'section', attrs: {} },
    { tag: 'ul', attrs: {} },
    { tag: 'li', attrs: {} },
  ];
  // Full chain in order matches.
  assert.equal(selectorMatches('ul > li > span', 'span', {}, chain), true);
  // Wrong order does not.
  assert.equal(selectorMatches('li > ul > span', 'span', {}, chain), false);
  // Mixed: descendant hop over <ul> then exact child.
  assert.equal(selectorMatches('section li > span', 'span', {}, chain), true);
});

test('wave8: mixed chains backtrack — greedy nearest-ancestor must not false-negative', () => {
  // `.a > .b .c target`: the NEAREST .b candidate (index 2) has parent .b,
  // not .a — only the farther .b (index 1, parent .a) satisfies the child
  // step. A greedy right-to-left matcher without backtracking fails here.
  const chain = [
    { tag: 'div', attrs: { class: 'a' } },
    { tag: 'div', attrs: { class: 'b' } },
    { tag: 'div', attrs: { class: 'b' } },
    { tag: 'div', attrs: { class: 'c' } },
  ];
  assert.equal(selectorMatches('.a > .b .c span', 'span', {}, chain), true);
});

test('wave8: child-combinator rules never degrade to rightmost-only without ancestors', () => {
  // Descendant chains keep the legacy "rightmost compound only" fallback
  // (pinned above in 'descendant — applies last compound'), but a child
  // chain matching any bare <div> would be a structural over-match.
  assert.equal(selectorMatches('.flex > div', 'div', {}), false);
  assert.equal(selectorMatches('.parent .target', 'div', { class: 'target' }), true);
});

test('wave8: buildComponents lands `.flex > div` rules on the nested child (abspos-autopos shape)', () => {
  // Minimal reproduction of css-flexbox/abspos/abspos-autopos-htb-ltr.html.
  const html = '<body><div class="flex"><div></div></div></body>';
  const rules = parseCss(
    '.flex { display: flex; width: 100px; height: 100px; background: red }\n' +
    '.flex > div { position: absolute; width: 100%; height: 100%; background: green }',
  );
  const { components } = buildComponents(html, rules, 't');
  assert.equal(components['t__0'].properties.display, 'flex');
  const child = components['t__0'].children['t__0__0'];
  // The child rule must land — previously this was a 100x100 placeholder.
  assert.equal(child.properties.position, 'absolute');
  assert.equal(child.properties.background, 'green');
  // The parent must NOT receive the child rule.
  assert.equal(components['t__0'].properties.position, undefined);
});

// ── wave-8: support-asset inlining ─────────────────────────────────────────
//
// Regression source: css-break background-image-000/001/002 shipped
// `url(../support/cat.png)` verbatim into the IR — a 404 on every platform
// (0.14–0.38 SSIM everywhere; pure asset-delivery gap).

test('wave8: percentEncodeBytes encodes EVERY byte as lowercase %xx', () => {
  // 'A' (0x41) MUST be encoded — a literal 'A' would be corrupted by the
  // converter's value lowercasing; %41 survives (hex digits decode
  // case-insensitively per RFC 3986 §2.1).
  assert.equal(percentEncodeBytes(Buffer.from([0x41])), '%41');
  assert.equal(percentEncodeBytes(Buffer.from([0x89, 0x50, 0x4e, 0x47])), '%89%50%4e%47');
  assert.equal(percentEncodeBytes(Buffer.from([0x0a, 0xff, 0x00])), '%0a%ff%00');
  // Empty buffer → empty string.
  assert.equal(percentEncodeBytes(Buffer.from([])), '');
});

test('wave8: inlineUrlsInValue inlines a small raster asset as a percent-encoded data URI', async () => {
  // Write a tiny fake PNG into a temp dir and reference it relatively.
  const os = await import('node:os');
  const path = await import('node:path');
  const { promises: fsp } = await import('node:fs');
  const dir = await fsp.mkdtemp(path.join(os.tmpdir(), 'wave8-inline-'));
  const bytes = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x00, 0x41]);
  await fsp.writeFile(path.join(dir, 'cat.png'), bytes);
  const { value, inlined, unresolved } =
    await inlineUrlsInValue('red url(cat.png) left top', dir);
  assert.equal(inlined, 1);
  assert.equal(unresolved, 0);
  // Unquoted url(), lowercase mime, every byte percent-encoded.
  assert.equal(value, 'red url(data:image/png,%89%50%4e%47%00%41) left top');
  await fsp.rm(dir, { recursive: true, force: true });
});

test('wave8: inlineUrlsInValue leaves data:/http(s)/#fragment/sub-template refs untouched', async () => {
  const cases = [
    'url(data:image/png,%00)',           // already inline
    'url(https://example.com/x.png)',    // cross-origin — bucketer domain
    'url(#frag)',                        // same-document SVG ref
    'url(http://{{hosts[][]}}/support/x.png)', // WPT sub-template token
  ];
  for (const v of cases) {
    const r = await inlineUrlsInValue(v, '/nonexistent-base');
    assert.equal(r.value, v, `should be untouched: ${v}`);
    assert.equal(r.inlined, 0);
    assert.equal(r.unresolved, 0, `out-of-scope refs are NOT lossy: ${v}`);
  }
});

test('wave8: oversize + missing + non-raster assets stay verbatim and count unresolved', async () => {
  const os = await import('node:os');
  const path = await import('node:path');
  const { promises: fsp } = await import('node:fs');
  const dir = await fsp.mkdtemp(path.join(os.tmpdir(), 'wave8-lossy-'));
  // Oversize: exactly MAX_INLINE_ASSET_BYTES (cap is strict `<`).
  await fsp.writeFile(path.join(dir, 'big.png'), Buffer.alloc(MAX_INLINE_ASSET_BYTES));
  // Non-raster: svg is text, deliberately excluded from the raster scope.
  await fsp.writeFile(path.join(dir, 'vec.svg'), '<svg/>');
  for (const v of ['url(big.png)', 'url(missing.png)', 'url(vec.svg)']) {
    const r = await inlineUrlsInValue(v, dir);
    assert.equal(r.value, v, `verbatim: ${v}`);
    assert.equal(r.unresolved, 1, `unresolved: ${v}`);
  }
  await fsp.rm(dir, { recursive: true, force: true });
});

test('wave8: inlineFixtureAssets marks components + _wpt lossy for undeliverable assets', async () => {
  const fixture = {
    _wpt: { test: 'x.html', lossy: false, lossyReasons: [] },
    components: {
      a: { properties: { background: 'url(nope.png)' } },
      b: {
        properties: {},
        children: { b__0: { id: 'b__0', properties: { 'background-image': 'url(also-nope.png)' } } },
      },
    },
  };
  const { inlined, unresolved } = await inlineFixtureAssets(fixture, '/nonexistent-base');
  assert.equal(inlined, 0);
  assert.equal(unresolved, 2);
  // Component-level honest markers (tag matches wpt-not-applicable Rule 20).
  assert.equal(fixture.components.a._lossy, true);
  assert.deepEqual(fixture.components.a._lossyReasons, ['requires-bundled-asset']);
  // Nested children get their own marker.
  assert.equal(fixture.components.b.children.b__0._lossy, true);
  // Top-level rollup.
  assert.equal(fixture._wpt.lossy, true);
  assert.ok(fixture._wpt.lossyReasons.includes('requires-bundled-asset'));
});

test('wave8: inlineFixtureAssets leaves ref-shaped _wpt blocks untouched', async () => {
  // Ref fixtures' _wpt is `{ref, of, specSection}` — no lossy fields. The
  // inliner must not invent them (component markers still apply).
  const fixture = {
    _wpt: { ref: 'x-ref.html', of: 'x.html', specSection: 's' },
    components: { a: { properties: { background: 'url(nope.png)' } } },
  };
  await inlineFixtureAssets(fixture, '/nonexistent-base');
  assert.equal(fixture._wpt.lossy, undefined);
  assert.equal(fixture._wpt.lossyReasons, undefined);
  assert.equal(fixture.components.a._lossy, true);
});

// ── wave-12 EXTRACTOR-INLINE: pure-inline run merging ───────────────────────
//
// The cross-platform inline-run fragmentation: the standard WPT reftest
// preamble `<p>Test passes if there is a filled green square and
// <strong>no red</strong>.</p>` used to flatten to the glued own-text
// 'Test passes if there is a filled green square and .' PLUS a separate
// <strong> child component that every runtime stacks as a BLOCK — 3 lines
// vs the browser-ref's 2, displacing everything below by ~+20px on all
// three platforms (and the orphan 'and .' token split Android/iOS line
// breaking per UAX#14 LB13). The wave-12 short-term contract merges pure-
// inline children into the parent's `_text` in reading order, dropping the
// inline styling and marking the component lossy ('inline-run-merged').
// The real interleaved inline-runs wire is v3-gated roadmap
// (schema/spec/05-versioning.md — v2 is the frozen current wire).

// The exact preamble innerHtml every abspos-autopos / abspos-in-* /
// css-grid descendant test carries (and their shared 100px-square ref).
const PREAMBLE_INNER =
  'Test passes if there is a filled green square and <strong>no red</strong>.';
// The exact single string the merge must produce — 'and no red.' with the
// run spliced at its reading-order position, NOT the glued 'and .'.
const PREAMBLE_MERGED =
  'Test passes if there is a filled green square and no red.';

test('wave12: extractOwnTextMerged merges the standard preamble to the exact single string', () => {
  const r = extractOwnTextMerged(PREAMBLE_INNER, { styledTags: new Set() });
  assert.equal(r.text, PREAMBLE_MERGED);
  // Exactly one absorbed run — drives the lossy marker downstream.
  assert.equal(r.merged, 1);
});

test('wave12: extractOwnTextMerged without a mergeCtx matches extractOwnText (legacy)', () => {
  // Null ctx = merging off — byte-identical to the pre-wave-12 glued text.
  const r = extractOwnTextMerged(PREAMBLE_INNER, null);
  assert.equal(r.text, extractOwnText(PREAMBLE_INNER));
  assert.equal(r.text, 'Test passes if there is a filled green square and .');
  assert.equal(r.merged, 0);
});

test('wave12: isPureInlineMergeable pins all four guard conditions', () => {
  // 1. Phrase-content tags qualify; block-ish tags never do.
  assert.equal(isPureInlineMergeable('strong', {}, 'no red'), true);
  assert.equal(isPureInlineMergeable('em', {}, 'x'), true);
  assert.equal(isPureInlineMergeable('div', {}, 'x'), false);
  assert.equal(isPureInlineMergeable('p', {}, 'x'), false);
  // 2. Nested elements keep the child-component path.
  assert.equal(isPureInlineMergeable('strong', {}, 'a <span>b</span>'), false);
  // 3. ANY attribute disqualifies — id/class/style are style hooks, dir
  //    flips bidi; conservatism protects styled subjects.
  assert.equal(isPureInlineMergeable('span', { class: 'target' }, 'x'), false);
  assert.equal(isPureInlineMergeable('span', { id: 't' }, 'x'), false);
  assert.equal(isPureInlineMergeable('strong', { style: 'color:red' }, 'x'), false);
  // 4. A rule targeting the tag directly keeps it a component.
  assert.equal(isPureInlineMergeable('strong', {}, 'x', new Set(['strong'])), false);
  assert.equal(isPureInlineMergeable('strong', {}, 'x', new Set(['div'])), true);
});

test('wave12: collectStyledTags collects rightmost-compound tags only', () => {
  const rules = parseCss(
    'strong { color: red }' +          // bare tag → collected
    '.note em { font-style: normal }' + // rightmost em → collected
    'span::before { content: "x" }' +  // pseudo-element host span → collected
    '.flex > div { width: 10px }' +    // rightmost div → collected
    '* { margin: 0 }' +                // universal — deliberately NOT collected
    'code + b { color: blue }',        // sibling combinator → unsupported, skipped
  );
  const tags = collectStyledTags(rules);
  assert.deepEqual([...tags].sort(), ['div', 'em', 'span', 'strong']);
});

test('wave12: extractBodyTreeNested absorbs the preamble strong (no child node)', () => {
  const html = `<body><p>${PREAMBLE_INNER}</p><div class="flex"><div></div></div></body>`;
  const tree = extractBodyTreeNested(html, 5, { styledTags: new Set() });
  // The <p> keeps NO children — the strong was absorbed into ownText.
  assert.equal(tree[0].tag, 'p');
  assert.deepEqual(tree[0].children, []);
  assert.equal(tree[0].ownText, PREAMBLE_MERGED);
  // The absorbed node is flagged so buildComponents can lossy-mark it.
  assert.equal(tree[0].inlineMerged, true);
  // The block sibling is untouched (and un-flagged).
  assert.equal(tree[1].tag, 'div');
  assert.equal(tree[1].inlineMerged, undefined);
});

test('wave12: a nested-element strong does NOT merge (keeps the child path)', () => {
  // <strong> wrapping an element is real structure — merging would need
  // recursive flattening and would hide the inner element from the
  // renderer. It must stay a child component, text un-glued as before.
  const html = '<body><p>before <strong>keep <span>inner</span></strong> after</p></body>';
  const tree = extractBodyTreeNested(html, 5, { styledTags: new Set() });
  assert.equal(tree[0].children.length, 1);
  assert.equal(tree[0].children[0].tag, 'strong');
  // Parent text stays the (glued) own-text — the strong was NOT absorbed.
  assert.equal(tree[0].ownText, 'before after');
  assert.equal(tree[0].inlineMerged, undefined);
});

test('wave12: buildComponents merges the abspos-autopos preamble end-to-end', () => {
  // Minimal reproduction of css-flexbox/abspos/abspos-autopos-htb-ltr.html
  // (the shape shared by css-break abspos-in-* + css-grid descendants).
  const html =
    `<body><p>${PREAMBLE_INNER}</p>` +
    '<div class="flex"><div></div></div></body>';
  const rules = parseCss(
    '.flex { display: flex; width: 100px; height: 100px; background: red }' +
    '.flex > div { position: absolute; width: 100px; height: 100px; background: green }',
  );
  const { components, lossyOverall, lossyReasons } = buildComponents(html, rules, 't');
  const p = components['t__0'];
  // The exact merged sentence, ONE component, NO strong child.
  assert.equal(p._text, PREAMBLE_MERGED);
  assert.equal(p.children, undefined);
  // The lossy marker lands on the component AND rolls up to the fixture.
  assert.equal(p._lossy, true);
  assert.ok(p._lossyReasons.includes('inline-run-merged'));
  assert.equal(lossyOverall, true);
  assert.ok(lossyReasons.includes('inline-run-merged'));
  // The flex subject is untouched by the merge.
  assert.equal(components['t__1'].properties.display, 'flex');
  assert.equal(components['t__1'].children['t__1__0'].properties.background, 'green');
});

test('wave12: a tag-targeted strong stays a child component (styledTags guard)', () => {
  // `strong { color: red }` — the declaration under test MUST land, so the
  // merge is suppressed and the pre-wave-12 child path is preserved.
  const html = `<body><p>${PREAMBLE_INNER}</p></body>`;
  const rules = parseCss('strong { color: red }');
  const { components, lossyReasons } = buildComponents(html, rules, 't');
  const p = components['t__0'];
  // Glued text + separate strong child — exactly the legacy shape.
  assert.equal(p._text, 'Test passes if there is a filled green square and .');
  const childKeys = Object.keys(p.children);
  assert.equal(childKeys.length, 1);
  assert.equal(p.children[childKeys[0]]._tag, 'strong');
  assert.equal(p.children[childKeys[0]].properties.color, 'red');
  // No merge happened → no inline-run-merged marker anywhere.
  assert.ok(!lossyReasons.includes('inline-run-merged'));
});

test('wave12: leading body text absorbs pure-inline runs and keeps ordering (wave-11 interplay)', () => {
  // Both features apply: bare body prose CONTAINING an inline element
  // before the first block. The leading run must carry the full sentence,
  // the strong must not double-emit as a component, and __text must still
  // precede __0 in map insertion order (the renderers' document order).
  const html =
    '<body>There should be <strong>no red</strong>: <div class="t"></div></body>';
  const rules = parseCss('.t { width: 20px }');
  const { components, lossyReasons } = buildComponents(html, rules, 'x');
  assert.equal(components['x__text']._text, 'There should be no red:');
  // The absorbed run lossy-marks the __text component + the roll-up.
  assert.equal(components['x__text']._lossy, true);
  assert.deepEqual(components['x__text']._lossyReasons, ['inline-run-merged']);
  assert.ok(lossyReasons.includes('inline-run-merged'));
  // Exactly __text + the block sibling — no strong component anywhere.
  const keys = Object.keys(components);
  assert.deepEqual(keys, ['x__text', 'x__0']);
  assert.equal(components['x__0'].properties.width, '20px');
});

test('wave12: extractLeadingBodyTextInfo without mergeCtx keeps wave-11 semantics', () => {
  // Legacy contract: the run stops at the FIRST renderable element of any
  // kind — inline or block — and reports zero merges.
  const html = '<body>There should be <strong>no red</strong>: <div></div></body>';
  const r = extractLeadingBodyTextInfo(html, null);
  assert.equal(r.text, 'There should be');
  assert.equal(r.merged, 0);
  // The string wrapper delegates to the same path.
  assert.equal(extractLeadingBodyText(html), 'There should be');
});

test('wave12: body-level inline runs AFTER the first block keep the component path', () => {
  // Only the LEADING run is absorbed into __text. A body-level inline
  // element after the first block sibling has no other text carrier, so
  // it must remain a component — text is never silently lost.
  const html = '<body><div class="t"></div><em>tail note</em></body>';
  const rules = parseCss('.t { width: 20px }');
  const { components } = buildComponents(html, rules, 'x');
  // No __text (body opens with an element), block first, em second.
  assert.equal(components['x__text'], undefined);
  assert.equal(components['x__0'].properties.width, '20px');
  assert.equal(components['x__1']._text, 'tail note');
  assert.equal(components['x__1']._tag, 'em');
});

// ── wave-13 KEYFRAMES-SAMPLER ───────────────────────────────────────────────
//
// Static sampling of WPT's time-stable negative-delay animations. The pins
// below mirror the CONFIRMED wave-13 audit finding: the measured test
// (css-backgrounds/animations/background-color-animation-in-body.html) must
// bake background-color rgb(100, 100, 0); a positive-delay control must NOT
// sample; the cubic-bezier evaluator is pinned at the known midpoint plus
// its endpoints.

test('wave13: parseKeyframes reads from/to + percentage keys', () => {
  // The measured test's exact keyframes block.
  const kf = parseKeyframes(
    '@keyframes bgcolor { 0% { background-color: rgb(0, 200, 0); } 100% { background-color: rgb(200, 0, 0); } }');
  assert.deepEqual(kf.bgcolor, [
    { offset: 0, props: { 'background-color': 'rgb(0, 200, 0)' } },
    { offset: 1, props: { 'background-color': 'rgb(200, 0, 0)' } },
  ]);
  // from/to aliases map to 0/1 (css-animations-1 §4 keyframe-selector).
  const kf2 = parseKeyframes('@keyframes m { from { opacity: 0 } to { opacity: 1 } }');
  assert.equal(kf2.m[0].offset, 0);
  assert.equal(kf2.m[1].offset, 1);
});

test('wave13: parseKeyframes fans out comma key lists and captures frame easing', () => {
  // "0%, 100% { … }" produces one frame per key; a frame-level
  // animation-timing-function lands in `easing`, not props (§4 "Timing
  // functions for keyframes").
  const kf = parseKeyframes(
    '@keyframes k { 0%, 100% { width: 10px } 50% { width: 20px; animation-timing-function: linear } }');
  assert.equal(kf.k.length, 3);
  assert.deepEqual(kf.k.map((f) => f.offset), [0, 0.5, 1]);
  assert.equal(kf.k[1].easing, 'linear');
  assert.equal(kf.k[1].props['animation-timing-function'], undefined);
});

test('wave13: parseKeyframes ignores !important and honours last-name-wins', () => {
  // §4: !important inside keyframes is IGNORED; §3: the last @keyframes
  // block with a given name wins wholesale.
  const kf = parseKeyframes(
    '@keyframes k { 0% { width: 1px !important; height: 2px } }' +
    '@keyframes k { 0% { width: 9px } }');
  assert.deepEqual(kf.k, [{ offset: 0, props: { width: '9px' } }]);
});

test('wave13: cubic-bezier evaluator pinned at the measured point + endpoints', () => {
  // The measured WPT easing: cubic-bezier(0,1,1,0) at x=0.5 is exactly 0.5
  // (the curve is symmetric about the midpoint, slope zero — the whole
  // reason WPT picked it for time-stable screenshots).
  assert.ok(Math.abs(cubicBezierY(0, 1, 1, 0, 0.5) - 0.5) < 1e-6);
  // Endpoint pins: y(0)=0 and y(1)=1 within bisection precision.
  assert.ok(Math.abs(cubicBezierY(0, 1, 1, 0, 0) - 0) < 1e-6);
  assert.ok(Math.abs(cubicBezierY(0, 1, 1, 0, 1) - 1) < 1e-6);
});

test('wave13: parseTimingFunction handles keywords, functions, and rejects junk', () => {
  // Keywords map to their css-easing-1 §2.1 bezier equivalents.
  assert.deepEqual(parseTimingFunction('ease-in'), { type: 'bezier', x1: 0.42, y1: 0, x2: 1, y2: 1 });
  // cubic-bezier() parses its four control numbers.
  assert.deepEqual(parseTimingFunction('cubic-bezier(0,1,1,0)'), { type: 'bezier', x1: 0, y1: 1, x2: 1, y2: 0 });
  // §2.3: x-coordinates outside [0,1] make the function INVALID.
  assert.equal(parseTimingFunction('cubic-bezier(2,0,1,1)'), null);
  // steps() with default (end) and explicit positions.
  assert.deepEqual(parseTimingFunction('steps(4)'), { type: 'steps', n: 4, pos: 'jump-end' });
  assert.deepEqual(parseTimingFunction('steps(2, start)'), { type: 'steps', n: 2, pos: 'jump-start' });
  // The linear() function and arbitrary idents are unsupported → null.
  assert.equal(parseTimingFunction('linear(0, 0.5 25%, 1)'), null);
  assert.equal(parseTimingFunction('bouncy'), null);
});

test('wave13: evalTimingFunction — linear identity and steps positions', () => {
  // linear shortcut is exact (no bisection noise).
  assert.equal(evalTimingFunction(parseTimingFunction('linear'), 0.3), 0.3);
  // steps(4, end) at 0.3: floor(1.2)/4 = 0.25 (css-easing-1 §3.9.2).
  assert.equal(evalTimingFunction(parseTimingFunction('steps(4)'), 0.3), 0.25);
  // step-start jumps immediately: (floor(0.3)+1)/1 = 1.
  assert.equal(evalTimingFunction(parseTimingFunction('step-start'), 0.3), 1);
  // step-end holds at 0 until the end: floor(0.3)/1 = 0.
  assert.equal(evalTimingFunction(parseTimingFunction('step-end'), 0.3), 0);
});

test('wave13: sRGB color parse + premultiplied lerp', () => {
  // Legacy comma rgb() and 4-argument rgb() (modern grammar) both parse.
  assert.deepEqual(parseSrgbColor('rgb(0, 200, 0)'), { r: 0, g: 200, b: 0, a: 1 });
  assert.deepEqual(parseSrgbColor('rgb(0, 200, 0, 0)'), { r: 0, g: 200, b: 0, a: 0 });
  // Unsupported syntaxes refuse instead of guessing.
  assert.equal(parseSrgbColor('hsl(120, 50%, 50%)'), null);
  // The measured pin: opaque endpoints lerp per-channel in sRGB
  // (css-color-4 §13 legacy caveat — Chromium's behavior on these tests).
  assert.equal(lerpSrgbColors(parseSrgbColor('rgb(0, 200, 0)'), parseSrgbColor('rgb(200, 0, 0)'), 0.5),
               'rgb(100, 100, 0)');
  // Alpha-0 endpoints: premultiplied interpolation zeroes the channels —
  // the transparent-animation-in-body sibling bakes rgba(0, 0, 0, 0).
  assert.equal(lerpSrgbColors(parseSrgbColor('rgba(0, 200, 0, 0)'), parseSrgbColor('rgba(200, 0, 0, 0)'), 0.5),
               'rgba(0, 0, 0, 0)');
});

test('wave13: lerpCssValue — numerics need matching units, colors lerp, junk refuses', () => {
  // Same-unit scalar lerp.
  assert.equal(lerpCssValue('0px', '100px', 0.5), '50px');
  // Bare numbers (opacity-style) lerp too.
  assert.equal(lerpCssValue('10', '20', 0.25), '12.5');
  // Unit mismatch is out of scope (no calc() synthesis) → null.
  assert.equal(lerpCssValue('10px', '50%', 0.5), null);
  // Identical strings are a static segment regardless of parseability.
  assert.equal(lerpCssValue('url(a.png)', 'url(a.png)', 0.7), 'url(a.png)');
});

test('wave13: parseAnimationDecl — first time is duration, second is delay', () => {
  // The measured shorthand, exactly as extracted from the WPT test.
  const d = parseAnimationDecl({ animation: 'bgcolor 1000000s cubic-bezier(0,1,1,0) -500000s' });
  assert.equal(d.name, 'bgcolor');
  assert.equal(d.durationMs, 1000000 * 1000);
  assert.equal(d.delayMs, -500000 * 1000);
  assert.equal(d.easing, 'cubic-bezier(0,1,1,0)');
  // Longhands override their shorthand slot.
  const d2 = parseAnimationDecl({
    'animation-name': 'k', 'animation-duration': '10s', 'animation-delay': '-5s',
  });
  assert.equal(d2.name, 'k');
  assert.equal(d2.durationMs, 10000);
  assert.equal(d2.delayMs, -5000);
  // Comma-separated multi-animation lists are out of the bounded scope.
  assert.equal(parseAnimationDecl({ animation: 'a 1s -0.5s, b 2s' }), null);
});

test('wave13 PIN: the measured WPT test bakes background-color rgb(100, 100, 0)', () => {
  // css-backgrounds/animations/background-color-animation-in-body.html:
  // progress = 500000/1000000 = 0.5; cubic-bezier(0,1,1,0) at 0.5 = 0.5;
  // rgb(0,200,0) → rgb(200,0,0) at 0.5 = rgb(100, 100, 0) — the exact
  // uniform color the WPT ref paints.
  const kf = parseKeyframes(
    '@keyframes bgcolor { 0% { background-color: rgb(0, 200, 0); } 100% { background-color: rgb(200, 0, 0); } }');
  const s = sampleKeyframesAnimation(
    { animation: 'bgcolor 1000000s cubic-bezier(0,1,1,0) -500000s' }, kf);
  assert.deepEqual(s, {
    baked: { 'background-color': 'rgb(100, 100, 0)' },
    dropped: ['animation'],
  });
});

test('wave13 PIN: positive-delay and zero-delay controls do NOT sample', () => {
  const kf = parseKeyframes(
    '@keyframes bgcolor { 0% { background-color: rgb(0, 200, 0); } 100% { background-color: rgb(200, 0, 0); } }');
  // Positive delay: the screenshot would race the timeline — boundary rule.
  assert.equal(sampleKeyframesAnimation(
    { animation: 'bgcolor 1000000s cubic-bezier(0,1,1,0) 500000s' }, kf), null);
  // Zero delay (the with-images / with-tableN family): same boundary.
  assert.equal(sampleKeyframesAnimation({ animation: 'bgcolor 100s' }, kf), null);
});

test('wave13: out-of-scope declarations extract verbatim (null sample)', () => {
  const kf = parseKeyframes('@keyframes k { 0% { width: 0px } 100% { width: 100px } }');
  // Unknown keyframes name — nothing to sample.
  assert.equal(sampleKeyframesAnimation({ animation: 'ghost 10s -5s' }, kf), null);
  // Non-normal direction: directed-progress mapping is out of scope.
  assert.equal(sampleKeyframesAnimation({ animation: 'k 10s -5s reverse' }, kf), null);
  // Progress ≥ 1 (delay magnitude ≥ duration): fill-mode-dependent.
  assert.equal(sampleKeyframesAnimation({ animation: 'k 10s -10s' }, kf), null);
  // Missing bracketing frame: keyframes starting at 50% leave 0–50% to the
  // UNDERLYING value (css-animations-1 §4), unknowable statically.
  const partial = parseKeyframes('@keyframes p { 50% { width: 0px } 100% { width: 100px } }');
  assert.equal(sampleKeyframesAnimation({ animation: 'p 10s -2s' }, partial), null);
});

test('wave13: per-keyframe timing function governs its own segment', () => {
  // Mirrors background-color-animation-three-keyframes1.html: -50000s of
  // 1000000s = progress 0.05, inside the [0%, 10%] segment → local 0.5.
  // The 0% frame declares no easing, so the ELEMENT-level
  // cubic-bezier(0,1,1,0) applies → eased 0.5 → rgb(100, 100, 0).
  const kf = parseKeyframes(
    '@keyframes bg { 0% { background-color: rgb(0, 200, 0); }' +
    ' 10% { background-color: rgb(200, 0, 0); animation-timing-function: cubic-bezier(0,1,1,0); }' +
    ' 100% { background-color: rgb(0, 0, 200); animation-timing-function: cubic-bezier(0,1,1,0); } }');
  const s = sampleKeyframesAnimation(
    { animation: 'bg 1000000s cubic-bezier(0,1,1,0) -50000s' }, kf);
  assert.deepEqual(s.baked, { 'background-color': 'rgb(100, 100, 0)' });
  // Frame-level easing OVERRIDES the element's: same geometry but the 0%
  // frame pins `linear`, so eased local = 0.5 still lerps the same pair —
  // pin via a step function instead to observe the difference: step-end
  // holds the 0% value across the whole segment.
  const kf2 = parseKeyframes(
    '@keyframes bg { 0% { background-color: rgb(0, 200, 0); animation-timing-function: step-end; }' +
    ' 10% { background-color: rgb(200, 0, 0); } 100% { background-color: rgb(0, 0, 200); } }');
  const s2 = sampleKeyframesAnimation(
    { animation: 'bg 1000000s cubic-bezier(0,1,1,0) -50000s' }, kf2);
  assert.deepEqual(s2.baked, { 'background-color': 'rgb(0, 200, 0)' });
});

test('wave13: buildComponents bakes body-root animations with the sampled-animation note', () => {
  // Integration pin — the measured test's shape: body-scope animation +
  // keyframes. The synthetic __body component must carry the baked color,
  // no animation-* keys, and the LOUD _lossy/'sampled-animation' marker.
  const css = 'body { animation: bgcolor 1000000s cubic-bezier(0,1,1,0) -500000s; }';
  const rules = parseCss(css);
  const kf = parseKeyframes(
    '@keyframes bgcolor { 0% { background-color: rgb(0, 200, 0); } 100% { background-color: rgb(200, 0, 0); } }');
  const built = buildComponents('<body></body>', rules, 'x', null, kf);
  const body = built.components['x__body'];
  assert.deepEqual(body.properties, { 'background-color': 'rgb(100, 100, 0)' });
  assert.equal(body._lossy, true);
  assert.deepEqual(body._lossyReasons, ['sampled-animation']);
  assert.ok(built.lossyReasons.includes('sampled-animation'));
  // Legacy call WITHOUT the keyframes arg: sampling disabled, animation
  // declaration rides through verbatim (byte-identical pre-wave-13 path).
  const legacy = buildComponents('<body></body>', rules, 'x');
  assert.equal(legacy.components['x__body'].properties.animation,
               'bgcolor 1000000s cubic-bezier(0,1,1,0) -500000s');
});

test('wave13: element-path sampling drops longhands too and lossy-scans the baked bag', () => {
  // An element-scoped animation via LONGHANDS; the sampled width lands as
  // a %-free px value, and every animation-* key is dropped.
  const css = '.t { animation-name: grow; animation-duration: 100s; animation-delay: -50s; animation-timing-function: linear; }';
  const rules = parseCss(css);
  const kf = parseKeyframes('@keyframes grow { 0% { width: 0px } 100% { width: 100px } }');
  const built = buildComponents('<body><div class="t"></div></body>', rules, 'x', null, kf);
  const cmp = built.components['x__0'];
  assert.deepEqual(cmp.properties, { width: '50px' });
  assert.deepEqual(cmp._lossyReasons, ['sampled-animation']);
});

// ── wave-15 BIDI-EXTRACT: character references, dir attribute, pre family ────
//
// Pinned against the css-text bidi sources the wave-15 diagnosis named:
//   tools/wpt/css/css-text/bidi/bidi-tab-001.html   (span dir=ltr>&#9;0)
//   tools/wpt/css/css-text/bidi/bidi-lines-001.html (white-space: pre lines)
//   tools/wpt/css/css-text/white-space/tab-bidi-001.html (literal TABs)

test('wave15: decodeCharacterReferences decodes numeric + named refs once', () => {
  // The load-bearing bidi-tab-001 pin: '&#9;0' is a TAB followed by '0'.
  assert.equal(decodeCharacterReferences('&#9;0'), '\t0');
  // Hex form + named XML-core forms.
  assert.equal(decodeCharacterReferences('&#x41;&lt;b&gt;&quot;&apos;'), 'A<b>"\'');
  // Single pass, no rescan: '&amp;#9;' is the LITERAL '&#9;', not a TAB —
  // the same answer the HTML tokenizer gives.
  assert.equal(decodeCharacterReferences('&amp;#9;'), '&#9;');
  // Unknown named refs stay verbatim (conservative), bare '&' untouched.
  assert.equal(decodeCharacterReferences('&nosuchref; a & b'), '&nosuchref; a & b');
  // Spec-shaped numeric error handling: NUL / out-of-range / surrogate
  // code points all decode to U+FFFD instead of throwing.
  assert.equal(decodeCharacterReferences('&#0;&#x110000;&#xD800;'), '���');
  // Bidi controls from the named subset (RLM is U+200F).
  assert.equal(decodeCharacterReferences('a&rlm;b'), 'a‏b');
});

test('wave15: scanners decode refs then apply the white-space rule in that order', () => {
  // Under the default collapse, a decoded TAB is COLLAPSIBLE white space
  // (CSS Text §4.1): leading '&#9;' vanishes into the trim — proving the
  // decode happens BEFORE the collapse, exactly the browser pipeline.
  assert.equal(extractOwnText('&#9;0'), '0');
  // Preserve mode (pre family): the decoded TAB survives verbatim.
  assert.equal(extractOwnTextMerged('&#9;0', null, true).text, '\t0');
  // Leading-body-text path shares both the decode and the preserve switch.
  assert.equal(extractLeadingBodyTextInfo('<body>&#9;ok<div></div></body>', null, true).text, '\tok');
  assert.equal(extractLeadingBodyTextInfo('<body>&#9;ok<div></div></body>', null).text, 'ok');
  // Preserve mode still yields '' for whitespace-ONLY runs (head-newline
  // noise in the no-<body> fallback must not become a phantom __text).
  assert.equal(extractLeadingBodyTextInfo('<body>\n  \n<div></div></body>', null, true).text, '');
});

test('wave15: firstStrongDirection implements the UAX#9 first-strong approximation', () => {
  // The bidi-lines-001 word pair: Persian is strong RTL, French strong LTR.
  assert.equal(firstStrongDirection('فارسی'), 'rtl');
  assert.equal(firstStrongDirection('français'), 'ltr');
  // Weak/neutral prefixes (digits, punctuation, space) are skipped — the
  // FIRST STRONG character decides (UAX#9 P2).
  assert.equal(firstStrongDirection('123 !? א'), 'rtl');
  // Hebrew block, presentation forms, and empty/no-strong fallbacks.
  assert.equal(firstStrongDirection('שלום'), 'rtl');
  assert.equal(firstStrongDirection(''), 'ltr');
  assert.equal(firstStrongDirection('123...'), 'ltr');
});

test('wave15: dirAttributeDirection maps ltr/rtl/auto and rejects invalid values', () => {
  // The two literal states, ASCII case-insensitively (HTML §15.3.4).
  assert.equal(dirAttributeDirection({ dir: 'rtl' }), 'rtl');
  assert.equal(dirAttributeDirection({ dir: 'LTR' }), 'ltr');
  // dir=auto resolves through the first-strong scan over the given text.
  assert.equal(dirAttributeDirection({ dir: 'auto' }, 'سلام'), 'rtl');
  assert.equal(dirAttributeDirection({ dir: 'auto' }, 'Hello'), 'ltr');
  // bidi-tab-001's intentional `dir=ltrl` typo: invalid value → no
  // directionality state → null (inherits as if the attribute were absent).
  assert.equal(dirAttributeDirection({ dir: 'ltrl' }), null);
  // No dir attribute at all → null.
  assert.equal(dirAttributeDirection({}), null);
});

test('wave15: buildComponents — bidi-tab-001 shape: TAB survives pre, dir maps to direction', () => {
  // Minimal reproduction of the bidi-tab-001 structure: a pre container
  // whose span child (dir attr keeps it off the inline-merge path) carries
  // the '&#9;0' text, with resolved white-space INHERITED from the div.
  const css = 'div { white-space: pre; width: 10ch; } span { background: yellow; }';
  const rules = parseCss(css);
  const html = '<body><div dir=rtl><span dir=ltr>&#9;0</span></div></body>';
  const built = buildComponents(html, rules, 'x');
  const div = built.components['x__0'];
  // dir=rtl on the div maps to the CSS direction property at extract time.
  assert.equal(div.properties.direction, 'rtl');
  const span = div.children['x__0__0'];
  // The span inherits white-space: pre from the div, so the decoded TAB
  // survives into _text — THE wave-15 fix (was the literal 6-char '&#9;0').
  assert.equal(span._text, '\t0');
  assert.equal(span.properties.direction, 'ltr');
});

test('wave15: buildComponents — author direction beats the dir attribute hint', () => {
  // HTML §15.3.4 maps dir as a PRESENTATIONAL hint: author-origin CSS
  // (here an inline style, as tab-bidi-001's third rows use) must win.
  const built = buildComponents(
    '<body><div dir=rtl style="direction: ltr">x</div></body>', [], 'x');
  assert.equal(built.components['x__0'].properties.direction, 'ltr');
});

test('wave15: buildComponents — dir=auto bakes a direction and rides the lossy lane', () => {
  // dir=auto is resolved at extract time (runtimes cannot re-resolve);
  // the baked heuristic result must be LOUD via 'dir-auto-resolved'.
  const built = buildComponents('<body><div dir=auto>سلام</div></body>', [], 'x');
  const cmp = built.components['x__0'];
  assert.equal(cmp.properties.direction, 'rtl');
  assert.equal(cmp._lossy, true);
  assert.ok(cmp._lossyReasons.includes('dir-auto-resolved'));
  assert.ok(built.lossyReasons.includes('dir-auto-resolved'));
});

test('wave15: buildComponents — white-space pre preserves source newlines (bidi-lines-001)', () => {
  // bidi-lines-001 shape: a pre div whose own text is a newline-separated
  // run of alternating-direction words. The newlines must survive.
  const css = 'div { white-space: pre; width: 10em; }';
  const built = buildComponents(
    '<body><div>français\nفارسی\nfrançais</div></body>',
    parseCss(css), 'x');
  assert.equal(built.components['x__0']._text,
               'français\nفارسی\nfrançais');
});

test('wave15: buildComponents — non-pre elements keep the legacy collapse byte-identically', () => {
  // Sanity guard: without a pre-family rule the collapse (and its trim)
  // behaves exactly as before wave-15 — no fixture churn outside pre.
  const built = buildComponents('<body><div>  a\n\tb  </div></body>', [], 'x');
  assert.equal(built.components['x__0']._text, 'a b');
});

// ── wave-17 BODY-HEIGHT SLOTTING ────────────────────────────────────────────
//
// css-backgrounds/background-attachment-fixed-inside-transform-1: the test
// styles `body { height: 4000px }`. Pre-wave-17 the extractor emitted the
// body-root as a SIZED sibling block and stacked the body's children BELOW
// it, so #outer (which overlaps the body in the real page, rotated band
// around y≈340) rendered at y≈4340 on all three platforms — and the mostly-
// white diff passed VACUOUSLY at 0.96 (lowContentDensity). The fix slots the
// body's children INTO the body-root's `children` map when (and only when)
// the body declares an explicit nonzero ABSOLUTE height. See the decision
// record above buildComponents in extract-fixture.mjs.

test('wave17: bodyDeclaresAbsoluteHeight — trigger edge set', () => {
  // Absolute nonzero lengths trigger (the sized-sibling-block failure mode).
  assert.equal(bodyDeclaresAbsoluteHeight({ height: '4000px' }), true);
  assert.equal(bodyDeclaresAbsoluteHeight({ height: '3in' }), true);
  assert.equal(bodyDeclaresAbsoluteHeight({ 'block-size': '200px' }), true);
  // Fractional + case-insensitive unit forms parse too.
  assert.equal(bodyDeclaresAbsoluteHeight({ height: '.5PX' }), true);
  // No height at all → no trigger (the overwhelmingly common corpus case).
  assert.equal(bodyDeclaresAbsoluteHeight({}), false);
  assert.equal(bodyDeclaresAbsoluteHeight({ margin: '0' }), false);
  // auto / CSS-wide keywords → no sized block, no trigger.
  assert.equal(bodyDeclaresAbsoluteHeight({ height: 'auto' }), false);
  assert.equal(bodyDeclaresAbsoluteHeight({ height: 'inherit' }), false);
  // Zero heights (css-overflow body-propagation family) → geometry is
  // identical either way; stay byte-for-byte on the legacy shape.
  assert.equal(bodyDeclaresAbsoluteHeight({ height: '0' }), false);
  assert.equal(bodyDeclaresAbsoluteHeight({ height: '0px' }), false);
  // Percentages resolve to auto against the auto-height composed canvas;
  // viewport/font-relative units and functions normalize to null in the IR
  // (runtime-dependent) — none materialize a sized sibling block today.
  assert.equal(bodyDeclaresAbsoluteHeight({ height: '100%' }), false);
  assert.equal(bodyDeclaresAbsoluteHeight({ height: '300vh' }), false);
  assert.equal(bodyDeclaresAbsoluteHeight({ height: '10em' }), false);
  assert.equal(bodyDeclaresAbsoluteHeight({ height: 'calc-size(auto, size)' }), false);
});

test('wave17: sized body slots element children under the body-root (transform-1 shape)', () => {
  // Mirror of background-attachment-fixed-inside-transform-1.html.
  const css =
    'body { height: 4000px; margin: 0 }' +
    '#outer { margin: 200px; height: 700px; width: 300px }' +
    '#inner { height: 700px; background-color: lime }';
  const html = '<body><div id="outer"><div id="inner"></div></div></body>';
  const { components } = buildComponents(html, parseCss(css), 't');
  // ONE top-level entry: the body-root. No `t__0` sibling stacked below it.
  assert.deepEqual(Object.keys(components), ['t__body']);
  const body = components['t__body'];
  assert.equal(body._role, 'body-root');
  // The body-root KEEPS its declared height (it must still paint/measure
  // 4000px so the composed canvas's natural height matches the ref's
  // documentHeight) — the height is not stripped or rewritten.
  assert.equal(body.properties.height, '4000px');
  // #outer nests under the body-root with its legacy id, in the child-map
  // shape buildNode emits ({ id, ... } entries keyed by id — the Kotlin
  // parser contract at CssParsing.kt:96).
  assert.deepEqual(Object.keys(body.children), ['t__0']);
  const outer = body.children['t__0'];
  assert.equal(outer.id, 't__0');
  assert.equal(outer.properties.margin, '200px');
  // #inner's own nesting under #outer is untouched by the slotting.
  assert.equal(outer.children['t__0__0'].properties['background-color'], 'lime');
});

test('wave17: sized body slots the leading __text run under the body-root too', () => {
  // Leading anonymous body prose is a <body> child like any element — under
  // a sized body its line box paints INSIDE the body area (real-page
  // structure), so it rides the same slotting switch, ahead of __0 in the
  // child map's insertion order (the renderers' document order).
  const css = 'body { height: 300px } .t { width: 20px }';
  const html = '<body>There should be no red:\n<div class="t"></div></body>';
  const { components } = buildComponents(html, parseCss(css), 't');
  assert.deepEqual(Object.keys(components), ['t__body']);
  const kids = Object.keys(components['t__body'].children);
  assert.deepEqual(kids, ['t__text', 't__0']);
  assert.equal(components['t__body'].children['t__text']._text, 'There should be no red:');
});

test('wave17: body WITHOUT explicit height keeps the sibling shape byte-for-byte', () => {
  // The extractor pin the fix promises: a body that declares no absolute
  // height — background-only body rules, percentage heights, zero heights —
  // emits the LEGACY flat shape (body-root + __N siblings), unchanged.
  for (const bodyDecl of ['background: green', 'height: 100%', 'height: 0px']) {
    const css = `body { ${bodyDecl} } .t { width: 20px }`;
    const html = '<body><div class="t"></div></body>';
    const { components } = buildComponents(html, parseCss(css), 't');
    // Two top-level entries: the body-root FIRST, then the element sibling.
    assert.deepEqual(Object.keys(components), ['t__body', 't__0'],
      `body { ${bodyDecl} } must keep the flat sibling shape`);
    // No children map materializes on the body-root.
    assert.equal(components['t__body'].children, undefined);
    // The sibling keeps the legacy no-id top-level shape.
    assert.equal(components['t__0'].id, undefined);
    assert.equal(components['t__0'].properties.width, '20px');
  }
});

test('wave17: sized body with NO element children emits no empty children map', () => {
  // background-attachment-margin-root-001 shape (sized body, empty <body>):
  // the omit-when-empty rule holds — no `children: {}` key appears.
  const { components } = buildComponents('<body></body>',
    parseCss('body { height: 300px }'), 't');
  assert.deepEqual(Object.keys(components), ['t__body']);
  assert.equal(components['t__body'].children, undefined);
});

// ── wave-20 POST-LOAD STRUCTURE: the htmlOverride input source ──────────────
//
// post-load-extract.mjs re-feeds the SERIALIZED post-script DOM through
// extractFixture via `opts.htmlOverride` — the override must swap ONLY the
// input document, never fork the pipeline. One source pin (the wiring line)
// plus one corpus-gated behavioral pin (created nodes become components,
// head-derived fields like the ref link survive).

import { readFileSync as _rfs, existsSync as _exists } from 'node:fs';
import { join as _join, dirname as _dirname } from 'node:path';
import { fileURLToPath as _furl } from 'node:url';

test('wave20: extractFixture htmlOverride swaps only the input source', () => {
  // Source-scan pin (the inject-wpt-block.test convention for wiring): the
  // override must feed the same `html` variable the disk read feeds — every
  // downstream step (stylesheets, ref, assets) then runs unchanged on it.
  const src = _rfs(new URL('./extract-fixture.mjs', import.meta.url), 'utf8');
  assert.match(src, /const html = opts\.htmlOverride \?\? await fs\.readFile\(testAbs, 'utf8'\)/,
    'htmlOverride must substitute the html input and nothing else');
});

// Behavioral pin against the real corpus + bucket index — skip-guarded so
// the default hermetic run stays green on checkouts without tools/wpt.
const _repoRoot = _join(_dirname(_furl(import.meta.url)), '..', '..');
const _wptDir = process.env.WPT_DIR ?? _join(_repoRoot, 'tools', 'wpt');
const _overrideTest = 'css/css-ui/appearance-auto-non-html-namespace-001.html';
const _overrideReady = _exists(_join(_wptDir, _overrideTest))
  && _exists(_join(_repoRoot, 'tools', 'titan', 'wpt-buckets.json'));

test('wave20: htmlOverride makes script-created nodes first-class components', { skip: !_overrideReady }, async () => {
  const { extractFixture } = await import('./extract-fixture.mjs');
  // The static document has an EMPTY <div id=div>; the override simulates
  // the serialized post-script DOM where the script appended two children.
  const staticHtml = _rfs(_join(_wptDir, _overrideTest), 'utf8');
  const override = staticHtml.replace('<div id=div></div>',
    '<div id=div><button></button><meter></meter></div>');
  const { fixture } = await extractFixture(_overrideTest, { htmlOverride: override });
  const stem = 'appearance-auto-non-html-namespace-001';
  // The created children exist as components under the div (path 1 → 1.0/1.1).
  const div = fixture.components[`${stem}__1`];
  assert.ok(div.children?.[`${stem}__1__0`], 'created <button> must be a component');
  assert.ok(div.children?.[`${stem}__1__1`], 'created <meter> must be a component');
  // Head-derived fields still come from the (identical) head: the rel=match
  // ref resolves exactly as the static pass resolved it.
  assert.equal(fixture._wpt.ref, 'css/css-ui/nothing-below-ref.html');
  // The override path does NOT stamp anything — stamps are the post-load
  // module's job AFTER its cross-checks pass.
  assert.equal(fixture._wpt.postLoadExtracted, undefined);
  assert.equal(fixture._wpt.structureExtracted, undefined);
});

// ── wave-20 lane W1: widget-identity attributes (_attrs wire) ───────────────
//
// The cross-lane wire contract: for form/widget tags, `_attrs` carries ONLY
// the present-in-source attributes among the ten allow-listed keys, typed
// strings except booleans (checked/multiple/selected/disabled → true) and
// numbers-where-numeric (min/max everywhere; value on meter/progress).
// Pinned here at BOTH altitudes: the pure widgetAttrsFor table and the
// buildComponents emission (placement beside `_tag`, omit-when-empty).

test('w1: widgetAttrsFor — non-widget tags never emit attrs', async () => {
  const { widgetAttrsFor } = await import('./extract-fixture.mjs');
  // <div type=x> is not a widget; attrs stay selector fuel only.
  assert.equal(widgetAttrsFor('div', { type: 'x' }), null);
  // Absent/odd tags are equally null (defensive contract edge).
  assert.equal(widgetAttrsFor(null, { type: 'x' }), null);
});

test('w1: widgetAttrsFor — present-only keys, allow-list filtered', async () => {
  const { widgetAttrsFor } = await import('./extract-fixture.mjs');
  // id/class/style are NOT forwarded; only allow-listed present keys are.
  const out = widgetAttrsFor('input', { type: 'text', value: 'input-text', id: 'x', class: 'y', style: 'color:red' });
  assert.deepEqual(out, { type: 'text', value: 'input-text' });
  // No qualifying attribute at all → null (fixture omits `_attrs` entirely).
  assert.equal(widgetAttrsFor('input', { id: 'x' }), null);
  assert.equal(widgetAttrsFor('button', {}), null);
});

test('w1: widgetAttrsFor — boolean attributes are presence-true', async () => {
  const { widgetAttrsFor } = await import('./extract-fixture.mjs');
  // Bare attribute (walkChildren stores '') → true.
  assert.deepEqual(widgetAttrsFor('input', { type: 'checkbox', checked: '' }),
    { type: 'checkbox', checked: true });
  // HTML §2.3.2: any value means true — 'checked="checked"' included.
  assert.deepEqual(widgetAttrsFor('input', { checked: 'checked' }), { checked: true });
  assert.deepEqual(widgetAttrsFor('select', { multiple: '' }), { multiple: true });
  assert.deepEqual(widgetAttrsFor('option', { selected: '' }), { selected: true });
  assert.deepEqual(widgetAttrsFor('button', { disabled: '' }), { disabled: true });
});

test('w1: widgetAttrsFor — numeric lanes (min/max always; value on meter/progress)', async () => {
  const { widgetAttrsFor } = await import('./extract-fixture.mjs');
  // meter/progress value → Number (HTML §4.10.13/14 float reflections).
  assert.deepEqual(widgetAttrsFor('meter', { value: '0.5' }), { value: 0.5 });
  assert.deepEqual(widgetAttrsFor('progress', { value: '0.5' }), { value: 0.5 });
  // input value stays a STRING even when numeric-looking (text content).
  assert.deepEqual(widgetAttrsFor('input', { value: '42' }), { value: '42' });
  // min/max numeric on any widget tag.
  assert.deepEqual(widgetAttrsFor('input', { type: 'range', min: '-1.5', max: '1e2' }),
    { type: 'range', min: -1.5, max: 100 });
  // Non-numeric min/max text stays verbatim (the "where numeric" clause).
  assert.deepEqual(widgetAttrsFor('input', { min: '2026-01-01' }), { min: '2026-01-01' });
});

test('w1: buildComponents emits _attrs beside _tag for widget elements', () => {
  // The appearance-checkbox-001 shape: attributed widgets under a container.
  const html =
    '<body><div id="container">' +
    '<input type="checkbox" checked>' +
    '<select multiple><option>select-multiple</option></select>' +
    '<meter value=0.5></meter>' +
    '</div></body>';
  const rules = parseCss('#container { width: 500px }');
  const { components } = buildComponents(html, rules, 'stem');
  const container = components['stem__0'];
  // input: type string + checked boolean, riding beside _tag.
  const input = container.children['stem__0__0'];
  assert.equal(input._tag, 'input');
  assert.deepEqual(input._attrs, { type: 'checkbox', checked: true });
  // select: boolean multiple; its <option> child carries NO attrs (none
  // authored) — omit-when-empty keeps the child byte-identical.
  const select = container.children['stem__0__1'];
  assert.equal(select._tag, 'select');
  assert.deepEqual(select._attrs, { multiple: true });
  const option = select.children['stem__0__1__0'];
  assert.equal(option._tag, 'option');
  assert.equal(option._attrs, undefined);
  assert.equal(option._text, 'select-multiple');
  // meter: unquoted numeric value → Number on the wire.
  const meter = container.children['stem__0__2'];
  assert.equal(meter._tag, 'meter');
  assert.deepEqual(meter._attrs, { value: 0.5 });
});

test('w1: buildComponents — attribute-free widgets and non-widgets stay byte-identical', () => {
  // <a>a</a> and <button>button</button> carry no allow-listed attributes:
  // no `_attrs` key may appear (pre-W1 fixture bytes preserved). A styled
  // <div> is the non-widget control.
  const html = '<body><a>a</a><button>button</button><div class="t"></div></body>';
  const { components } = buildComponents(html, parseCss('.t { color: red }'), 'stem');
  assert.equal(components['stem__0']._attrs, undefined);
  assert.equal(components['stem__1']._attrs, undefined);
  assert.equal(components['stem__2']._attrs, undefined);
  assert.deepEqual(Object.keys(components['stem__2']), ['properties']);
});

// ── wave-20 fix 5: the non-HTML-namespace gate ──────────────────────────────
//
// createElementNS('not-html', 'input') renders with NO widget chrome (the
// appearance-auto-non-html-namespace-001 pass condition), but the serialized
// live DOM flattens namespaces — so post-load-extract's serializer stamps
// FOREIGN_NS_MARKER_ATTR on non-XHTML elements while el.namespaceURI is
// still queryable, and buildNode suppresses `_tag`/`_attrs` on the marker.

test('fix5: FOREIGN_NS_MARKER_ATTR is the pinned wire marker', async () => {
  const { FOREIGN_NS_MARKER_ATTR } = await import('./extract-fixture.mjs');
  // The literal is a cross-file contract (post-load serializer stamps it,
  // buildNode gates on it) — pin it so neither side can drift silently.
  assert.equal(FOREIGN_NS_MARKER_ATTR, 'data-sc-foreign-ns');
});

test('fix5: the foreign-namespace marker suppresses _tag AND _attrs', async () => {
  const { FOREIGN_NS_MARKER_ATTR } = await import('./extract-fixture.mjs');
  // The serialized appearance-auto-non-html-namespace-001 shape: foreign
  // widget-named elements (marked by the serializer) next to a real one.
  const html = '<body><div id="div">' +
    `<input type="checkbox" ${FOREIGN_NS_MARKER_ATTR}="">` +
    `<button ${FOREIGN_NS_MARKER_ATTR}=""></button>` +
    '<input type="checkbox">' +
    '</div></body>';
  const { components } = buildComponents(html, parseCss('div * { width: 1em }'), 'stem');
  const div = components['stem__0'];
  // Foreign elements: generic-container treatment — no widget identity,
  // so no native ever paints a UA replica the browser-ref doesn't show.
  const foreignInput = div.children['stem__0__0'];
  assert.equal(foreignInput._tag, undefined);
  assert.equal(foreignInput._attrs, undefined);
  const foreignButton = div.children['stem__0__1'];
  assert.equal(foreignButton._tag, undefined);
  assert.equal(foreignButton._attrs, undefined);
  // The UNMARKED sibling keeps full widget identity — the gate is
  // per-element, never per-document.
  const htmlInput = div.children['stem__0__2'];
  assert.equal(htmlInput._tag, 'input');
  assert.deepEqual(htmlInput._attrs, { type: 'checkbox' });
});

// ═════════════════════════════════════════════════════════════════════════════
// wave-21 lane: five extraction defects (A-RC1, B-RC1, B-RC2, A-RC6, B-RC9a)
// ═════════════════════════════════════════════════════════════════════════════

// The new exports under test (dynamic import keeps the top-of-file import
// list untouched, mirroring the wave-20 widgetAttrsFor test pattern).
const {
  locateBodyContent,
  extractBodyTextRuns,
  foldTrivialCalcProducts,
  bakeSiblingIndex,
} = await import('./extract-fixture.mjs');

// ── A-RC1: head-unwrap silent loss ──────────────────────────────────────────

test('A-RC1: locateBodyContent keeps content AFTER a closed <head> (cross-fade shape)', () => {
  // The EXACT document shape of css-images/cross-fade-premultiplied-alpha:
  // <html> wrapper, closed <head>, body content with NO <body> tag.
  const html = '<!DOCTYPE html>\n<html>\n  <head>\n    <title>t</title>\n' +
    '    <style>div { width: 200px }</style>\n  </head>\n' +
    '  <p>There should be nearly no red.</p>\n  <div></div>\n</html>';
  const { inner, fallback } = locateBodyContent(html);
  assert.equal(fallback, true); // no <body>…</body> pair in the doc
  // The buffer must contain the body content…
  assert.ok(inner.includes('<p>There should be nearly no red.</p>'), `lost body content: ${inner}`);
  assert.ok(inner.includes('<div></div>'));
  // …and must NOT be the head's own content (the old group-2 bug).
  assert.equal(inner.includes('<title>'), false, 'buffer collapsed to head content');
});

test('A-RC1: an UNCLOSED <head> still unwraps to its interleaved content', () => {
  // No </head>: the head/body boundary is implicit (HTML §13.2.6.4.3) —
  // group 2 (everything after <head>) is kept and HEAD_ONLY filtering
  // separates scaffolding from content downstream. Pre-fix behaviour,
  // must not regress.
  const html = '<html><head><style>p { color: red }</style><p>prose</p>';
  const { inner } = locateBodyContent(html);
  assert.ok(inner.includes('<p>prose</p>'));
});

test('A-RC1: explicit <body> pair short-circuits (no unwrap, fallback=false)', () => {
  const html = '<html><head><title>x</title></head><body><div id=a></div></body></html>';
  const { inner, fallback } = locateBodyContent(html);
  assert.equal(fallback, false);
  assert.equal(inner, '<div id=a></div>');
});

test('A-RC1: third peel reaches an unclosed <body> after a closed <head>', () => {
  // <html> → <head> (closed, take-after) → <body…> (unclosed, group 2):
  // needs all three iterations — the old 2-iteration loop stopped short.
  const html = '<html><head><title>x</title></head><body class=t><div id=a></div>';
  const { inner } = locateBodyContent(html);
  assert.equal(inner.includes('<body'), false, 'body wrapper not peeled');
  assert.ok(inner.includes('<div id=a>'));
});

test('A-RC1: cross-fade shape end-to-end — components extracted, no phantom placeholder', () => {
  const html = '<!DOCTYPE html><html><head><title>t</title>' +
    '<style>div { margin: 2px; width: 200px; height: 200px }</style></head>' +
    '<p>There should be nearly no red.</p><div></div></html>';
  const { components } = buildComponents(html, parseCss('div { margin: 2px; width: 200px; height: 200px }'), 'cf');
  // Two real components: the instruction <p> (own text) + the styled div.
  assert.deepEqual(Object.keys(components), ['cf__0', 'cf__1']);
  assert.equal(components['cf__0']._text, 'There should be nearly no red.');
  assert.equal(components['cf__1'].properties.width, '200px');
});

// ── B-RC1: bare <br> → line-break component ─────────────────────────────────

test('B-RC1: a rule-less <br> emits a 0x20 line-break, not the 100x100 placeholder', () => {
  const { components } = buildComponents('<body><br><p>x</p></body>', [], 'br');
  const br = components['br__0'];
  // Height = the pinned REF line box (REF_LINE_HEIGHT 1.25 × 16px = 20px);
  // width 0 so an empty line carries no horizontal ink.
  assert.deepEqual(br.properties, { width: '0px', height: '20px' });
  assert.equal(br._role, 'line-break');
  assert.equal(br._tag, 'br'); // identity still forwarded
});

test('B-RC1: five body-level <br>s = five 20px line-breaks (500px phantom killed)', () => {
  const { components } = buildComponents('<br><br><br><br><br><p>There should be no red.</p>', [], 's');
  for (let i = 0; i < 5; i++) {
    assert.deepEqual(components[`s__${i}`].properties, { width: '0px', height: '20px' },
      `br #${i} not a line-break`);
  }
  assert.equal(components['s__5']._text, 'There should be no red.');
});

test('B-RC1: author declarations on <br> still win over the line-break base', () => {
  // Cascade honesty: a matched rule's height overrides the synthetic 20px.
  const { components } = buildComponents('<body><br></body>', parseCss('br { height: 7px }'), 'b');
  assert.equal(components['b__0'].properties.height, '7px');
  assert.equal(components['b__0'].properties.width, '0px'); // base survives where unset
  assert.equal(components['b__0']._role, 'line-break');
});

// ── wave-22 BR-LINE-CONTEXT: the <br> height rule (B-RC1 refinement) ────────
//
// Pins the two measured wave-21 regressions the refinement repairs
// (css-text empty-span-001 0.908→0.760; css-flexbox
// flex-abspos-staticpos-justify-self-001 0.98→0.93, all three platforms)
// while holding B-RC1's own case (the five-consecutive-brs test above)
// byte-identical.

test('wave-22: a <br> after inline content ends the line — height 0 (empty-span-001 shape)', () => {
  // The bidi/empty-span-001 body: inline <span> lines separated by <br>.
  // CSS 2.1 §9.5: the br merely terminates the line box the span opened —
  // the ref paints single-spaced lines, no 20px blanks between them.
  const html = '<span dir="auto">1;234;</span><br><span dir="auto">1;2;</span><br>';
  const { components } = buildComponents(html, [], 'e');
  assert.equal(components['e__1']._role, 'line-break');
  assert.deepEqual(components['e__1'].properties, { width: '0px', height: '0px' });
  assert.deepEqual(components['e__3'].properties, { width: '0px', height: '0px' });
});

test('wave-22: a clear-carrying <br> is a 0-height row-break marker (justify-self-001 shape)', () => {
  // The float-row pattern: floated containers broken by `<br clear:both>`.
  // The marker’s vertical contribution is CLEARANCE (§9.5.2) — the
  // engines' wave-19 FloatRowPacking strut (pin P6) models the row line
  // box, so a 20px marker height double-counts it (the measured 0.98→0.93
  // regression on all three platforms).
  const css = '.container { float: left; width: 16px; height: 10px } br { clear: both }';
  const html = '<div class="container"></div><div class="container"></div><br>' +
    '<div class="container"></div><div class="container"></div><br>';
  const { components } = buildComponents(html, parseCss(css), 'f');
  for (const key of ['f__2', 'f__5']) {
    assert.equal(components[key]._role, 'line-break', `${key} not a line-break`);
    assert.equal(components[key].properties.height, '0px', `${key} stacked a line box`);
    assert.equal(components[key].properties.clear, 'both'); // Clear still reaches the wire
  }
});

test('wave-22: text<br><br> — first br ends the line (0px), second is a blank line (20px)', () => {
  // The mixed case: only a LINE-START br produces an empty 20px line box.
  const { components } = buildComponents('<span id=s>text</span><br><br><p>after</p>', [], 'm');
  assert.equal(components['m__1'].properties.height, '0px');   // ends the text line
  assert.equal(components['m__2'].properties.height, '20px');  // blank line proper
});

test('wave-22: leading body prose arms the line — first <br> ends it, not a blank', () => {
  // The wave-11 leading `__text` run renders before element 0, so a br at
  // index 0 terminates the prose line instead of stacking a blank one.
  const { components } = buildComponents('some prose<br><p>x</p>', [], 'l');
  assert.equal(components['l__text']._text, 'some prose');
  assert.equal(components['l__0'].properties.height, '0px');
});

test('wave-22: a B-RC2 between-gap text run arms the line for the following <br>', () => {
  // Gap text (`__text<k>`) renders immediately before element k — a br
  // there ends the prose line (same rule as the leading run).
  const { components } = buildComponents('<div style="width:10px"></div>gap prose<br><p>x</p>', [], 'g');
  assert.equal(components['g__text1']._text, 'gap prose');
  assert.equal(components['g__1'].properties.height, '0px');
});

test('wave-22: floats do not arm the line — a clearless line-start <br> keeps its 20px', () => {
  // Out-of-flow siblings neither open nor end the line (§9.7): a br after
  // floats without `clear` is still a line-start br → blank line box.
  const css = '.f { float: left; width: 8px; height: 8px }';
  const { components } = buildComponents('<div class=f></div><br>', parseCss(css), 'n');
  assert.equal(components['n__1'].properties.height, '20px');
});

test('wave-22: block sibling closes the line — following <br> is a blank line again', () => {
  // inline → block → br: the block ended the inline line, so the br opens
  // (and closes) a fresh empty line box.
  const { components } = buildComponents(
    '<span id=a>t</span><div style="width:10px"></div><br>', [], 'k');
  assert.equal(components['k__2'].properties.height, '20px');
});

// ── B-RC2: body-level bare text between/after elements ──────────────────────

test('B-RC2: extractBodyTextRuns finds the run AFTER an element (auto-fill-print shape)', () => {
  // The exact css-multicol/auto-fill-auto-size-001-print body: one styled
  // empty <div>, then bare prose to EOF, no <body> tag.
  const html = '<!DOCTYPE html>\n<link rel="match" href="x-ref.html">\n' +
    '<div style="columns:2"></div>\nOn the first page\n';
  const runs = extractBodyTextRuns(html, { styledTags: new Set() });
  assert.deepEqual(runs, [{ afterElemIndex: 1, text: 'On the first page' }]);
});

test('B-RC2: leading text is NOT duplicated by the runs scanner (leading machinery owns it)', () => {
  const runs = extractBodyTextRuns('<body>lead text<div></div>tail</body>', { styledTags: new Set() });
  assert.deepEqual(runs, [{ afterElemIndex: 1, text: 'tail' }]);
});

test('B-RC2: runs between elements keep their gap indices (order preserved)', () => {
  const runs = extractBodyTextRuns('<body><div></div>one<p>p</p>two</body>', { styledTags: new Set() });
  assert.deepEqual(runs, [
    { afterElemIndex: 1, text: 'one' },
    { afterElemIndex: 2, text: 'two' },
  ]);
});

test('B-RC2: whitespace-only gaps emit nothing (markup noise, not prose)', () => {
  const runs = extractBodyTextRuns('<body><div></div>\n   \n<p>p</p></body>', { styledTags: new Set() });
  assert.deepEqual(runs, []);
});

test('B-RC2: buildComponents emits ordered __text<k> components for the runs', () => {
  const { components } = buildComponents('<body><div id=d></div>On the first page</body>', [], 'af');
  // Insertion order IS document order for every renderer: div, then run.
  assert.deepEqual(Object.keys(components), ['af__0', 'af__text1']);
  assert.equal(components['af__text1']._text, 'On the first page');
  assert.deepEqual(components['af__text1'].properties, {});
});

test('B-RC2: leading __text and a trailing run coexist without collision', () => {
  const { components } = buildComponents('<body>lead<div id=d></div>tail</body>', [], 'x');
  assert.deepEqual(Object.keys(components), ['x__text', 'x__0', 'x__text1']);
  assert.equal(components['x__text']._text, 'lead');
  assert.equal(components['x__text1']._text, 'tail');
});

test('B-RC2: run continues across head-only scaffolding (invisible in flow)', () => {
  const runs = extractBodyTextRuns(
    '<body><div></div>before <link rel=help href=h> after</body>',
    { styledTags: new Set() });
  assert.deepEqual(runs, [{ afterElemIndex: 1, text: 'before after' }]);
});

// ── A-RC6: sibling-index() extract-time bake ────────────────────────────────

test('A-RC6: foldTrivialCalcProducts folds unit*number and number*unit', () => {
  assert.equal(foldTrivialCalcProducts('calc(50deg * 2)'), '100deg');
  assert.equal(foldTrivialCalcProducts('calc(2 * 30px)'), '60px');
  assert.equal(foldTrivialCalcProducts('calc(0.1 * 3)'), '0.3'); // float noise trimmed
});

test('A-RC6: foldTrivialCalcProducts leaves non-trivial calc() verbatim', () => {
  // Sums, nested parens, double units: not products we may fold.
  assert.equal(foldTrivialCalcProducts('calc(50deg + 2deg)'), 'calc(50deg + 2deg)');
  assert.equal(foldTrivialCalcProducts('calc(2px * 3px)'), 'calc(2px * 3px)');
  assert.equal(foldTrivialCalcProducts('calc(var(--x) * 2)'), 'calc(var(--x) * 2)');
});

test('A-RC6: bakeSiblingIndex substitutes the 1-based index and folds', () => {
  const props = { background: 'conic-gradient(hsl(calc(50deg * sibling-index()) 100% 50%), green)' };
  const baked = bakeSiblingIndex(props, 2);
  assert.equal(baked, true);
  assert.equal(props.background, 'conic-gradient(hsl(100deg 100% 50%), green)');
});

test('A-RC6: bakeSiblingIndex is a no-op (returns false) without the function', () => {
  const props = { width: '100px' };
  assert.equal(bakeSiblingIndex(props, 3), false);
  assert.deepEqual(props, { width: '100px' });
});

test('A-RC6: end-to-end conic-gradient shape — absorbed <span> still counts as sibling #1', () => {
  // The EXACT css-images/conic-gradient-color-with-sibling-index shape:
  // an empty unstyled <span> (merge-absorbed at depth 0) then the styled
  // <div>. sibling-index() counts DOM element siblings, so the div is #2
  // even though the span never becomes a component.
  const css = 'div { width: 100px; height: 100px; background: conic-gradient(hsl(calc(50deg * sibling-index()) 100% 50%), green); }';
  const html = '<!DOCTYPE html><style>' + css + '</style><span></span><div></div>';
  const { components } = buildComponents(html, parseCss(css), 'cg');
  const div = components['cg__0'];
  assert.equal(div.properties.background, 'conic-gradient(hsl(100deg 100% 50%), green)');
  // LOUD provenance marker — informational, never score-excluding.
  assert.ok(div._lossyReasons.includes('baked-sibling-index'));
});

// ── B-RC9a: inline-run reorder honesty ──────────────────────────────────────

test('B-RC9a: mid-run styled span flags inline-run-reordered (non-decoration style)', () => {
  // <span> is rule-targeted → non-mergeable → child component; the parent's
  // _text glues 'the quick fox' and the child paints AFTER it — order
  // changed, and that must be LOUD (previously lossyReasons []).
  // wave-22 EX2 note: the rule declares `color`, which is NOT in
  // DECORATION_FAMILY_PROPS, so the B-RC4a inline-chain collapse refuses
  // this subtree and the wave-21 honesty flag still fires. The
  // decoration-styled variant of the SAME markup now collapses instead —
  // pinned in the 'B-RC4a' suite below.
  const css = 'span { color: red }';
  const { components, lossyReasons } = buildComponents(
    '<body><p>the quick <span>brown</span> fox</p></body>', parseCss(css), 'r');
  const p = components['r__0'];
  assert.equal(p._text, 'the quick fox');
  assert.ok(p._lossyReasons.includes('inline-run-reordered'));
  assert.ok(lossyReasons.includes('inline-run-reordered'));
});

test('B-RC9a: merged runs do NOT flag reorder (order preserved by the splice)', () => {
  // The standard WPT preamble: <strong> is absorbed IN PLACE — reading
  // order intact, only inline-run-merged applies.
  const { components } = buildComponents(
    '<body><p>square and <strong>no red</strong>.</p></body>', [], 'm');
  const p = components['m__0'];
  assert.equal(p._text, 'square and no red.');
  assert.ok(p._lossyReasons.includes('inline-run-merged'));
  assert.equal(p._lossyReasons.includes('inline-run-reordered'), false);
});

test('B-RC9a: text ONLY BEFORE the kept child does not flag (nothing glued across)', () => {
  const css = 'u { color: red }';
  const { components } = buildComponents(
    '<body><p>the quick <u>brown</u></p></body>', parseCss(css), 'nb');
  const p = components['nb__0'];
  assert.equal(p._text, 'the quick');
  assert.equal((p._lossyReasons ?? []).includes('inline-run-reordered'), false);
});

test('B-RC9a: whitespace-only tail after a kept child does not flag', () => {
  const css = 'u { color: red }';
  const { components } = buildComponents(
    '<body><p>lead <u>styled</u>\n  </p></body>', parseCss(css), 'ws');
  const p = components['ws__0'];
  assert.equal((p._lossyReasons ?? []).includes('inline-run-reordered'), false);
});

test('B-RC9a: text after a VOID child (<br>) flags too (br stays a component)', () => {
  const { components } = buildComponents(
    '<body><p>line1<br>line2</p></body>', [], 'v');
  const p = components['v__0'];
  assert.equal(p._text, 'line1line2');
  assert.ok(p._lossyReasons.includes('inline-run-reordered'));
});

// ── wave-22 EX2 A-RC1: :not() support ───────────────────────────────────────
//
// Root cause pinned by the wave21-final per-test IR: css-ui's
// appearance-menulist-button-001 is styled by exactly ONE rule,
// `#container > *:not(#drop-down-select) { appearance: menulist-button }`,
// and 'not' was missing from SUPPORTED_FUNCTIONAL_PSEUDOS — so the compound
// parsed as unsupported, the rule matched nothing, and all 13 widgets fell
// into the 100x100 placeholder (web-ref 0.554 against the ~1.000 alias band
// its eleven sibling appearance-* tests hold).

test('A-RC1 :not() — the menulist-button rule now matches every non-excluded child', () => {
  const rules = parseCss('#container > *:not(#drop-down-select) { appearance: menulist-button }');
  const anc = [{ tag: 'div', attrs: { id: 'container' }, pos: { isRoot: false, sibIndex: 0, sibCount: 1 } }];
  const pos = { isRoot: false, sibIndex: 0, sibCount: 16 };
  // Every widget EXCEPT the excluded select gets the declaration.
  assert.equal(propsForElement(rules, 'a', {}, anc, pos).props.appearance, 'menulist-button');
  assert.equal(propsForElement(rules, 'select', { multiple: '' }, anc, pos).props.appearance, 'menulist-button');
  // The negated id is the one child left alone — that IS the test's point
  // ("menulist-button is an alias to auto except on drop-down select").
  assert.deepEqual(propsForElement(rules, 'select', { id: 'drop-down-select' }, anc, pos).props, {});
});

test('A-RC1 :not() — tag / class arguments negate correctly', () => {
  const byTag = parseCss('div:not(p) { color: red }');
  assert.equal(propsForElement(byTag, 'div', {}, [], null).props.color, 'red');
  const byClass = parseCss('p:not(.skip) { color: red }');
  assert.equal(propsForElement(byClass, 'p', {}, [], null).props.color, 'red');
  assert.deepEqual(propsForElement(byClass, 'p', { class: 'skip' }, [], null).props, {});
});

test('A-RC1 :not() — an UNSUPPORTED inner selector drops the whole rule (no false match)', () => {
  // Attribute syntax, selector lists, combinators and nested :not() are all
  // outside the supported single-compound subset. Each must DROP the rule,
  // never negate to a blanket match — the "no silent fallthrough" rule.
  for (const sel of ['p:not([hidden])', 'p:not(.a, .b)', 'p:not(div p)', 'p:not(:not(.x))', 'p:not(::before)', 'p:not()']) {
    assert.deepEqual(
      propsForElement(parseCss(`${sel} { color: red }`), 'p', {}, [], null).props, {},
      `${sel} must not match`);
  }
});

test('A-RC1 :not() — a positional inner needs position data, else it bails', () => {
  const rules = parseCss('p:not(:first-child) { color: red }');
  // No `pos` → the inner is unknowable; negating an unknowable false would
  // manufacture a match, so we answer "no match" instead.
  assert.deepEqual(propsForElement(rules, 'p', {}, [], null).props, {});
  // With position data the negation is exact.
  assert.deepEqual(propsForElement(rules, 'p', {}, [], { isRoot: false, sibIndex: 0, sibCount: 2 }).props, {});
  assert.equal(propsForElement(rules, 'p', {}, [], { isRoot: false, sibIndex: 1, sibCount: 2 }).props.color, 'red');
});

test('A-RC1 :not() — collectStyledTags reads the compound without adding a phantom tag', () => {
  // `*:not(#id)` has no concrete host tag, so the merge guard must stay
  // empty (a phantom '*' entry would block every wave-12 inline merge).
  assert.deepEqual([...collectStyledTags(parseCss('#c > *:not(#x) { color: red }'))], []);
  // A concrete host tag still registers.
  assert.deepEqual([...collectStyledTags(parseCss('span:not(.x) { color: red }'))], ['span']);
});

// ── wave-22 EX2 A-RC1 part 2: rule-less widgets keep their intrinsic size ────

test('A-RC1 part 2: a rule-less <select> is NOT a 100x100 placeholder', () => {
  // The css-ui test deliberately excludes #drop-down-select from its only
  // rule; a 100x100 drop-down diverges from the ref far more than the UA
  // chrome it replaced (HTML Rendering §15.5 gives it an intrinsic size).
  const { components } = buildComponents(
    '<body><select id="drop-down-select"><option>select</option></select></body>', [], 'w');
  assert.deepEqual(components['w__0'].properties, {});
  assert.equal(components['w__0']._tag, 'select');
});

test('A-RC1 part 2: a rule-less LEAF <div> still IS a placeholder', () => {
  // The widget exemption is narrow — a plain EMPTY wrapper keeps the honest
  // empty-node box. wave-24 B-RC2 narrowed the branch by a second guard
  // (kept children), so the assertion moved to the INNER div: it is the one
  // with neither rules, own text, nor children.
  const { components } = buildComponents('<body><div><div></div></div></body>', [], 'd');
  const inner = components['d__0'].children['d__0__0'];
  assert.deepEqual(inner.properties, { width: '100px', height: '100px' });
});

test('B-RC2: a rule-less CONTAINER is NOT a placeholder — its children are its content', () => {
  // wave-24 B-RC2. The placeholder's premise is "no content of its own"; a
  // wrapper with KEPT children has content, and forcing 100×100 clips that
  // subtree into a hard box instead of adding a placeholder. Measured on
  // css-lists/change-list-style-type-001, whose ten un-classed <ul>s (no
  // matching rule, no own text) each became a 100×100 square hosting their
  // <li> against a ref of ten full-width list rows.
  const { components } = buildComponents('<body><div><div></div></div></body>', [], 'd');
  assert.deepEqual(components['d__0'].properties, {},
    'rule-less container must emit an EMPTY bag, like the ownText case');
  // The child is still emitted — the guard changes the parent's bag only.
  assert.equal(Object.keys(components['d__0'].children).length, 1);
});

test('B-RC2: a container whose children were all DROPPED keeps the placeholder', () => {
  // The guard reads the TREE node's KEPT children, not the raw DOM: an
  // element the walker emptied really is an empty node. A comment-only
  // wrapper is the narrow case — nothing survives the walk.
  const { components } = buildComponents('<body><div><!-- gone --></div></body>', [], 'c');
  assert.deepEqual(components['c__0'].properties, { width: '100px', height: '100px' });
});

test('A-RC1 part 2: INTRINSIC_WIDGET_TAGS excludes the chrome-less inline tags', () => {
  // `a` and `option` are plain inline/text elements — an empty one really
  // is scaffolding, so they must keep the placeholder.
  assert.equal(INTRINSIC_WIDGET_TAGS.has('a'), false);
  assert.equal(INTRINSIC_WIDGET_TAGS.has('option'), false);
  assert.equal(INTRINSIC_WIDGET_TAGS.has('select'), true);
});

// ── wave-22 EX2 B-RC4a: the scoped inline-chain collapse ────────────────────

test('B-RC4a flattenInlineText: document order, tags dropped, §4.1 collapse', () => {
  assert.equal(flattenInlineText('\n  the quick <u>brown</u> fox\n  '), 'the quick brown fox');
  // Character references decode at the tokenize→white-space boundary.
  assert.equal(flattenInlineText('a<span>&amp;</span>b'), 'a&b');
  // Pre-family callers keep every space verbatim.
  assert.equal(flattenInlineText(' a <b>c</b> ', true), ' a c ');
});

test('B-RC4a decorationContribution: longhand, shorthand, UA default, none', () => {
  // Longhand beats the shorthand's line component.
  assert.deepEqual(
    decorationContribution('span', { 'text-decoration': 'overline', 'text-decoration-line': 'underline' }),
    { lines: ['underline'], color: null, uaDerived: false });
  // Shorthand carries both the line and (by elimination) the colour.
  assert.deepEqual(
    decorationContribution('div', { 'text-decoration': 'dotted red underline' }),
    { lines: ['underline'], color: 'red', uaDerived: false });
  // Two lines in one declaration → two keywords, consumers get them split.
  assert.deepEqual(
    decorationContribution('span', { 'text-decoration-line': 'underline overline' }).lines,
    ['underline', 'overline']);
  // HTML Rendering §15.3.6 UA defaults for u/s/ins/del.
  assert.deepEqual(decorationContribution('u', {}), { lines: ['underline'], color: null, uaDerived: true });
  assert.deepEqual(decorationContribution('del', {}).lines, ['line-through']);
  // An authored declaration suppresses the UA fallback, `none` included —
  // per css-text-decor-3 §2.1 `none` never clears an ANCESTOR's line, it
  // just means this element contributes nothing.
  assert.deepEqual(decorationContribution('u', { 'text-decoration': 'none' }).lines, []);
  // A plain <span> has no UA decoration at all.
  assert.deepEqual(decorationContribution('span', {}), { lines: [], color: null, uaDerived: false });
});

test('B-RC4a: div>span>span>span collapses to ONE run with three ordered decorations', () => {
  // The css-text-decor/text-decoration-color.html shape, verbatim.
  const css = `
    #blue-underline  { text-decoration: underline;    text-decoration-color: blue }
    #gray-overline   { text-decoration: overline;     text-decoration-color: gray }
    #green-line-through { text-decoration: line-through; text-decoration-color: green }`;
  const html = '<body><div><span id="blue-underline"><span id="gray-overline">' +
    '<span id="green-line-through">TEXT</span></span></span></div></body>';
  const { components } = buildComponents(html, parseCss(css), 'c');
  const div = components['c__0'];
  // ONE component — the three stacked narrow boxes are gone.
  assert.equal(div.children, undefined);
  assert.equal(div._text, 'TEXT');
  // Outermost-first, one entry per line keyword, colours as authored.
  assert.deepEqual(div._decorations, [
    { line: 'underline', color: 'blue' },
    { line: 'overline', color: 'gray' },
    { line: 'line-through', color: 'green' },
  ]);
  // A full ancestor chain decorates ALL the run's text — nothing is
  // approximated, so the collapse marker must NOT fire.
  assert.equal((div._lossyReasons ?? []).includes('inline-chain-collapsed'), false);
  // The flat bag still carries a SUBSET (outermost-wins) for readers that
  // do not know `_decorations` — never a superset, so no double-painting.
  assert.equal(div.properties['text-decoration'], 'underline');
  assert.equal(div.properties['text-decoration-color'], 'blue');
});

test('B-RC4a: u-inside-h1 collapses in document order and clears inline-run-reordered', () => {
  // css-text-decor/text-decoration-inset-001: the <u> is rule-targeted so
  // wave-12 keeps it a component, wave-21 then flags the reorder. The
  // collapse restores true document order, so the flag must go.
  const css = 'u { text-decoration-color: black; text-decoration-inset: 10px -10px }';
  const html = '<body><div><h1>\n  the quick <u>brown</u> fox\n</h1></div></body>';
  const { components } = buildComponents(html, parseCss(css), 'h');
  const h1 = components['h__0'].children['h__0__0'];
  assert.equal(h1._text, 'the quick brown fox');
  assert.equal((h1._lossyReasons ?? []).includes('inline-run-reordered'), false);
  // The UA underline the extractor models no stylesheet for.
  assert.deepEqual(h1._decorations, [{ line: 'underline', color: 'black' }]);
  // The <u> holds only PART of the run, so painting its line over the whole
  // run IS an approximation — LOUD marker, not a silent widening.
  assert.ok(h1._lossyReasons.includes('inline-chain-collapsed'));
  // The link's authored decoration state folds into the flat bag.
  assert.equal(h1.properties['text-decoration-inset'], '10px -10px');
});

test('B-RC4a: a collapsed <h1> gains the UA heading metrics (2em/bold/margins in px)', () => {
  const css = 'u { text-decoration-color: black }';
  const { components } = buildComponents(
    '<body><h1>a <u>b</u> c</h1></body>', parseCss(css), 'ua');
  const h1 = components['ua__0'];
  // HTML Rendering §15.3.7 baked against the ref's 16px root (see UA_H1_PROPS).
  assert.equal(h1.properties['font-size'], '32px');
  assert.equal(h1.properties['font-weight'], 'bold');
  assert.equal(h1.properties['margin-top'], '21.44px');
  assert.ok(h1._lossyReasons.includes('ua-heading-defaults'));
});

test('B-RC4a: an author font-size on the h1 suppresses the UA metrics', () => {
  const css = 'u { text-decoration-color: black } h1 { font-size: 10px }';
  const { components } = buildComponents(
    '<body><h1>a <u>b</u> c</h1></body>', parseCss(css), 'uas');
  const h1 = components['uas__0'];
  assert.equal(h1.properties['font-size'], '10px');
  assert.equal((h1._lossyReasons ?? []).includes('ua-heading-defaults'), false);
});

test('B-RC4a: the decorating box wins the thickness (root-wins fold)', () => {
  // css-text-decor/decorating-box/…-thickness-001 asserts exactly this: the
  // div is the decorating box, so its 10px beats the span's 1px.
  const css = 'div { text-decoration: underline; text-decoration-thickness: 10px } ' +
              'span { text-decoration-thickness: 1px }';
  const { components } = buildComponents(
    '<body><div>\n  abc\n  <span>x</span>\n  def\n</div></body>', parseCss(css), 't');
  const div = components['t__0'];
  assert.equal(div._text, 'abc x def');
  assert.equal(div.properties['text-decoration-thickness'], '10px');
  assert.deepEqual(div._decorations, [{ line: 'underline' }]);
  // The span contributes no LINE, so nothing is painted over text it does
  // not contain — no approximation marker.
  assert.equal((div._lossyReasons ?? []).includes('inline-chain-collapsed'), false);
});

test('B-RC4a: currentColor is expressed by OMITTING the colour key', () => {
  // css-text-decor-3 §2.2 initial value. Absence means "use the text
  // colour" — it is the documented answer, never a silent drop.
  const { components } = buildComponents(
    '<body><div>a <u>b</u> c</div></body>', parseCss('u { text-decoration-style: wavy }'), 'cc');
  assert.deepEqual(components['cc__0']._decorations, [{ line: 'underline' }]);
});

// ── B-RC4a refusals: everything outside the narrow shape keeps today's form ──

test('B-RC4a refusal: a wrapper declaring a NON-decoration property', () => {
  // decorating-box-001's `<span style="vertical-align:-10px; color:transparent">`.
  const html = '<body><div>abc <u><span style="vertical-align: -10px; color: transparent">x</span> def</u></div></body>';
  const { components } = buildComponents(html, parseCss('u { text-decoration-color: black }'), 'r1');
  assert.equal(components['r1__0']._decorations, undefined);
  assert.ok(components['r1__0'].children); // stacked shape preserved
});

test('B-RC4a refusal: a non-decoration-only inline tag anywhere in the subtree', () => {
  // sub/sup shift the baseline, em/strong change weight/slant — flattening
  // would lose that, so INLINE_CHAIN_TAGS excludes them.
  for (const tag of ['sub', 'sup', 'em', 'strong', 'code', 'a']) {
    assert.equal(INLINE_CHAIN_TAGS.has(tag), false, `${tag} must not be collapsible`);
  }
  const html = '<body><div><sub id="d">x</sub></div></body>';
  const { components } = buildComponents(html, parseCss('#d { text-decoration: underline }'), 'r2');
  assert.equal(components['r2__0']._decorations, undefined);
});

test('B-RC4a refusal: a decoration-free nested span (the scope gate)', () => {
  // No decoration anywhere → no collapse. This gate is what keeps the
  // change inside the css-text-decor blast radius.
  const { components } = buildComponents(
    '<body><div><span id="x">TEXT</span></div></body>', parseCss('#x { color: red }'), 'r3');
  assert.equal(components['r3__0']._decorations, undefined);
  assert.ok(components['r3__0'].children);
});

test('B-RC4a refusal: a wrapper carrying a disallowed attribute', () => {
  // `dir` flips bidi — collapsing would silently change the run's order.
  const html = '<body><div><span dir="rtl" id="d">TEXT</span></div></body>';
  const { components } = buildComponents(html, parseCss('#d { text-decoration: underline }'), 'r4');
  assert.equal(components['r4__0']._decorations, undefined);
});

test('B-RC4a refusal: a wrapper with ::before generated content', () => {
  // CSS Generated Content L3 §3.2 — flattening would drop the content the
  // test is about.
  const css = '#d { text-decoration: underline } #d::before { content: "!" }';
  const { components } = buildComponents(
    '<body><div><span id="d">TEXT</span></div></body>', parseCss(css), 'r5');
  assert.equal(components['r5__0']._decorations, undefined);
});

test('B-RC4a: legacy walkers (no resolver) keep the pre-wave-22 tree exactly', () => {
  // extractBodyTreeNested called without a mergeCtx resolver — the shape
  // every pre-wave-22 unit test and direct caller pins.
  const tree = extractBodyTreeNested(
    '<body><div><span id="a"><span id="b">T</span></span></div></body>');
  assert.equal(tree[0].children.length, 1);
  assert.equal(tree[0].children[0].children.length, 1);
  assert.equal(tree[0].children[0].children[0].ownText, 'T');
  assert.equal(tree[0].decorations, undefined);
});

test('B-RC4a: collapseInlineRun returns null without a resolver', () => {
  // The production-only gate, pinned directly on the exported predicate.
  assert.equal(collapseInlineRun({ tag: 'div', children: [{}] }, '<u>x</u>', {}, null), null);
});

// ── wave-22 EX2 SKEPTIC: displaced decoration MODIFIERS must be loud ────────
//
// The outermost-wins fold drops a link's declaration whenever an outer box
// already wrote that key. `_decorations` entries are `{line, color?}` only,
// so a dropped STYLE / THICKNESS / INSET / OFFSET has no channel at all and
// used to vanish with `_lossyReasons: []`. Measured on the real corpus:
// css/css-text-decor/text-decoration-style-multiple.html nests three spans
// declaring `underline solid coral` / `overline dashed skyblue` /
// `line-through wavy green`; the collapse kept the first shorthand and three
// colourful entries, silently discarding `dashed` and `wavy` — the exact
// subject of that test. The repo's hard rule forbids that silence.

test('SKEPTIC: a displaced decoration STYLE marks inline-chain-decoration-dropped', () => {
  // Mirrors text-decoration-style-multiple: every link bears its own line,
  // so every link's style keyword is live state that the fold discards.
  const css = '#r { text-decoration: underline solid coral }' +
              '#a { text-decoration: overline dashed skyblue }' +
              '#b { text-decoration: line-through wavy green }';
  const { components, lossyReasons } = buildComponents(
    '<body><div id="r"><span id="a"><span id="b">AAAA</span></span></div></body>',
    parseCss(css), 'sk1');
  const c = components['sk1__0'];
  // The collapse still happens and the entry list is still complete for the
  // two fields it HAS — this marker is about the fields it does not have.
  assert.deepEqual(c._decorations, [
    { line: 'underline', color: 'coral' },
    { line: 'overline', color: 'skyblue' },
    { line: 'line-through', color: 'green' },
  ]);
  assert.ok(c._lossyReasons.includes('inline-chain-decoration-dropped'));
  assert.ok(lossyReasons.includes('inline-chain-decoration-dropped'));
});

test('SKEPTIC: a displaced THICKNESS on a line-bearing link is loud', () => {
  const css = '#r { text-decoration: underline; text-decoration-thickness: 10px }' +
              '#a { text-decoration: overline; text-decoration-thickness: 1px }';
  const { components } = buildComponents(
    '<body><div id="r"><span id="a">x</span></div></body>', parseCss(css), 'sk2');
  assert.ok(components['sk2__0']._lossyReasons.includes('inline-chain-decoration-dropped'));
});

test('SKEPTIC: decorating-box-thickness-001 stays CLEAN — an inert modifier is no loss', () => {
  // The exact WPT shape. The span declares NO line, so per css-text-decor-3
  // §1.3 the div is the decorating box and the span's 1px never paints —
  // dropping it loses nothing and must NOT raise the marker.
  const css = 'div { text-decoration: underline; text-decoration-thickness: 10px }' +
              'span { text-decoration-thickness: 1px }';
  const { components, lossyReasons } = buildComponents(
    '<body><div>abc <span>x</span> def</div></body>', parseCss(css), 'sk3');
  assert.equal(components['sk3__0']._text, 'abc x def');
  assert.equal(components['sk3__0']._lossy, undefined);
  assert.ok(!lossyReasons.includes('inline-chain-decoration-dropped'));
});

test('SKEPTIC: displaced LINE/COLOR alone stays clean (both survive in _decorations)', () => {
  // text-decoration-color.html's fourth block. Every link's line and colour
  // is re-expressed per entry, so the flat-bag displacement is the
  // documented lossless subset — no marker.
  const css = '#a { text-decoration: underline; text-decoration-color: blue }' +
              '#b { text-decoration: overline; text-decoration-color: gray }' +
              '#c { text-decoration: line-through; text-decoration-color: green }';
  const { components, lossyReasons } = buildComponents(
    '<body><div><span id="a"><span id="b"><span id="c">T</span></span></span></div></body>',
    parseCss(css), 'sk4');
  assert.equal(components['sk4__0']._decorations.length, 3);
  assert.ok(!lossyReasons.includes('inline-chain-decoration-dropped'));
});

test('SKEPTIC: an IDENTICAL redeclaration is not a loss', () => {
  // Same value on both boxes — the fold keeps one and nothing is discarded.
  const css = '#r { text-decoration: underline wavy red }' +
              '#a { text-decoration: underline wavy red }';
  const { components } = buildComponents(
    '<body><div id="r"><span id="a">x</span></div></body>', parseCss(css), 'sk5');
  assert.equal(components['sk5__0']._lossy, undefined);
});

test('SKEPTIC: carriesUnexpressibleDecoration classifies the shorthand components', () => {
  // Style keyword and thickness token have no `_decorations` field…
  assert.equal(carriesUnexpressibleDecoration('text-decoration', 'underline dashed red'), true);
  assert.equal(carriesUnexpressibleDecoration('text-decoration', '3px blue overline'), true);
  assert.equal(carriesUnexpressibleDecoration('text-decoration', 'underline from-font'), true);
  // …line + colour do.
  assert.equal(carriesUnexpressibleDecoration('text-decoration', 'underline overline'), false);
  assert.equal(carriesUnexpressibleDecoration('text-decoration', 'line-through green'), false);
  assert.equal(carriesUnexpressibleDecoration('text-decoration', 'none'), false);
  // The modifier longhands are unexpressible by definition; line/color are not.
  assert.equal(carriesUnexpressibleDecoration('text-decoration-style', 'wavy'), true);
  assert.equal(carriesUnexpressibleDecoration('text-underline-offset', '2px'), true);
  assert.equal(carriesUnexpressibleDecoration('text-decoration-line', 'underline'), false);
  assert.equal(carriesUnexpressibleDecoration('text-decoration-color', 'red'), false);
  // A non-decoration key is never this function's business.
  assert.equal(carriesUnexpressibleDecoration('color', 'red'), false);
});

// ── wave-23 BIDI BAKE: the static trigger ───────────────────────────────────
//
// The gate that decides whether tools/titan/bidi-bake.mjs launches a browser
// for a test. It is the ONLY promise that a pure-LTR test can never be
// reordered or repositioned by the bake, so its boundaries are pinned hard.

test('wave23: bidiBakeTrigger fires on RTL-script content', () => {
  assert.equal(bidiBakeTrigger('<p>فارسی</p>'), 'rtl-codepoint');   // Arabic
  assert.equal(bidiBakeTrigger('<p>שלום</p>'), 'rtl-codepoint');    // Hebrew
  // Astral RTL (Adlam) — the scan walks CODE POINTS, not UTF-16 units.
  assert.equal(bidiBakeTrigger('<p>\u{1E900}</p>'), 'rtl-codepoint');
});

test('wave23: bidiBakeTrigger fires on explicit bidi formatting controls', () => {
  // U+202E RIGHT-TO-LEFT OVERRIDE with otherwise pure-ASCII content.
  assert.equal(bidiBakeTrigger('<p>a‮b</p>'), 'bidi-control-codepoint');
  // U+2067 RIGHT-TO-LEFT ISOLATE.
  assert.equal(bidiBakeTrigger('<p>⁧x⁩</p>'), 'bidi-control-codepoint');
});

test('wave23: bidiBakeTrigger fires on dir=rtl|auto but NOT on dir=ltr', () => {
  assert.equal(bidiBakeTrigger('<div dir=rtl>x</div>'), 'dir-attribute');
  assert.equal(bidiBakeTrigger('<div dir="rtl">x</div>'), 'dir-attribute');
  assert.equal(bidiBakeTrigger("<div dir='auto'>x</div>"), 'dir-attribute');
  assert.equal(bidiBakeTrigger('<div dir = "AUTO">x</div>'), 'dir-attribute');
  // An author spelling out the default cannot create a reorder.
  assert.equal(bidiBakeTrigger('<div dir=ltr>x</div>'), null);
});

test('wave23: bidiBakeTrigger fires on the two bidi CSS properties', () => {
  assert.equal(bidiBakeTrigger('<style>p { direction: rtl }</style><p>x'), 'direction-rtl');
  assert.equal(bidiBakeTrigger('<p style="direction:rtl">x'), 'direction-rtl');
  assert.equal(bidiBakeTrigger('<style>p { unicode-bidi: plaintext }</style><p>x'), 'unicode-bidi');
  assert.equal(bidiBakeTrigger('<style>p { unicode-bidi:bidi-override }</style><p>x'), 'unicode-bidi');
  // `direction: ltr` and the initial `unicode-bidi` are not signals.
  assert.equal(bidiBakeTrigger('<style>p { direction: ltr }</style><p>x'), null);
  assert.equal(bidiBakeTrigger('<style>p { unicode-bidi: normal }</style><p>x'), null);
});

test('wave23: bidiBakeTrigger stays silent on ordinary LTR markup', () => {
  assert.equal(bidiBakeTrigger('<!DOCTYPE html><style>div{color:red}</style><div>Hello</div>'), null);
  assert.equal(bidiBakeTrigger(''), null);
  assert.equal(bidiBakeTrigger(undefined), null);
});

test('wave23: bidiBakeTrigger ignores COMMENTED-OUT bidi (comments strip first)', () => {
  // A commented declaration cannot reach layout, so it must not arm a
  // browser launch either — same discipline as every other static pass.
  assert.equal(bidiBakeTrigger('<style>/* direction: rtl */ p{color:red}</style><p>x'), null);
  assert.equal(bidiBakeTrigger('<!-- <div dir=rtl>x</div> --><p>y'), null);
});

test('wave23: the RTL ranges exclude the strong-L and neutral controls', () => {
  const inRanges = (cp) => RTL_CODEPOINT_RANGES.some(([lo, hi]) => cp >= lo && cp <= hi);
  assert.equal(inRanges(0x05D0), true);   // Hebrew alef
  assert.equal(inRanges(0x0627), true);   // Arabic alef
  assert.equal(inRanges(0xFEFC), true);   // last Arabic Presentation Forms-B letter
  // U+FEFF ZWNBSP is class BN, not RTL — including it would make the bake's
  // mixed-run guard bail on any text carrying a stray BOM.
  assert.equal(inRanges(0xFEFF), false);
  // U+200E LRM is strong L; it lives in the CONTROL list instead.
  assert.equal(inRanges(0x200E), false);
  assert.ok(BIDI_CONTROL_CODEPOINTS.includes(0x200E));
  assert.ok(BIDI_CONTROL_CODEPOINTS.includes(0x202E));
  assert.ok(BIDI_CONTROL_CODEPOINTS.includes(0x2069));
});

test('wave23: the trigger regexes are anchored on word boundaries', () => {
  // `redirection: rtl` must not read as `direction: rtl`.
  assert.equal(BIDI_DIRECTION_CSS_RX.test('redirection: rtl'), false);
  assert.equal(BIDI_DIRECTION_CSS_RX.test('direction: rtl'), true);
  // `data-dir=rtl` is not the HTML `dir` attribute.
  assert.equal(BIDI_DIR_ATTR_RX.test('<i data-dir=rtl>'), false);
  assert.equal(BIDI_DIR_ATTR_RX.test('<i dir=rtl>'), true);
  // The unicode-bidi probe is a NEGATIVE lookahead on `normal`, so
  // `normal` alone never matches but `normalise`-shaped values would —
  // there is no such CSS value, and every real one is caught.
  assert.equal(BIDI_UNICODE_BIDI_CSS_RX.test('unicode-bidi: normal'), false);
  assert.equal(BIDI_UNICODE_BIDI_CSS_RX.test('unicode-bidi: isolate'), true);
});

// ── wave-25 ATTR BAKE: end-to-end through buildComponents ────────────────────
//
// The unit surface of the bake lives in attr-bake.test.mjs; these pins cover
// the INTEGRATION — that the substitution really reaches the emitted
// component bag, carries its LOUD marker, and rolls up to the fixture level.

test('wave25: attr() bakes on the element bag, with the baked-attr marker', () => {
  // The exact css-values/attr-length-valid-zero shape: a same-rule
  // `width: 200px` overridden by a typed attr() whose attribute is "0".
  const css = '#o { background: red; width: 200px; width: attr(data-test type(<length>)); height: 200px; }';
  const html = '<!DOCTYPE html><style>' + css + '</style>'
    + '<body><div id="o" data-test="0"></div></body>';
  const { components, lossyReasons } = buildComponents(html, parseCss(css), 'ab');
  assert.equal(components['ab__0'].properties.width, '0');
  assert.ok(components['ab__0']._lossyReasons.includes('baked-attr'));
  assert.ok(lossyReasons.includes('baked-attr'));
});

test('wave25: attr() inside max() folds to the bare length', () => {
  // css-values/attr-in-max — the degenerate one-argument max() the
  // converter would otherwise classify as an unresolvable expression.
  const css = '#o { width: max(attr(data-test type(<length>))); height: 200px; }';
  const html = '<!DOCTYPE html><style>' + css + '</style>'
    + '<body><div id="o" data-test="200px"></div></body>';
  const { components } = buildComponents(html, parseCss(css), 'am');
  assert.equal(components['am__0'].properties.width, '200px');
});

test('wave25: a typed attr() with no value and no fallback becomes `unset`', () => {
  const css = 'span { background-color: attr(data-foo type(<color>)); }';
  const html = '<!DOCTYPE html><style>' + css + '</style>'
    + '<body><span data-foo="green">a</span><span>b</span></body>';
  const { components } = buildComponents(html, parseCss(css), 'ss');
  assert.equal(components['ss__0'].properties['background-color'], 'green');
  assert.equal(components['ss__1'].properties['background-color'], 'unset');
  assert.ok(components['ss__1']._lossyReasons.includes('attr-invalid-at-computed-value-time'));
});

test('wave25: a ::after attr() resolves against its ORIGINATING element', () => {
  const css = '#o::after { content: attr(data-mark, "fallback"); }';
  const html = '<!DOCTYPE html><style>' + css + '</style>'
    + '<body><div id="o" data-mark="hit"></div></body>';
  const { components } = buildComponents(html, parseCss(css), 'pe');
  const after = components['pe__0']._pseudo.after;
  assert.equal(after.properties.content, '"hit"');
  assert.ok(after._lossyReasons.includes('baked-attr'));
});

test('wave25: a namespace-qualified attr() ships verbatim + attr-unresolved', () => {
  const css = '#o { background: attr(*|bar type(*)); height: 100px; }';
  const html = '<!DOCTYPE html><style>' + css + '</style>'
    + '<body><div id="o" bar="red"></div></body>';
  const { components } = buildComponents(html, parseCss(css), 'ns');
  assert.equal(components['ns__0'].properties.background, 'attr(*|bar type(*))');
  assert.ok(components['ns__0']._lossyReasons.includes('attr-unresolved'));
});

test('wave25: the body-root scope is flagged, never baked', () => {
  // No single originating element exists for an html/body/:root bag.
  const css = 'body { background: attr(data-x type(<color>), green); height: 10px; }';
  const html = '<!DOCTYPE html><style>' + css + '</style><body><p>x</p></body>';
  const { components } = buildComponents(html, parseCss(css), 'br');
  assert.equal(components['br__body'].properties.background,
    'attr(data-x type(<color>), green)');
  assert.ok(components['br__body']._lossyReasons.includes('attr-unresolved'));
});

test('wave25: an attr()-free document is untouched by the bake', () => {
  // The corpus-identity guarantee, at the integration level: no marker, no
  // rewrite, no _lossy flag introduced anywhere.
  const css = 'div { width: 100px; height: 100px; background: green; }';
  const html = '<!DOCTYPE html><style>' + css + '</style><body><div></div></body>';
  const { components, lossyOverall, lossyReasons } = buildComponents(html, parseCss(css), 'na');
  assert.deepEqual(components['na__0'].properties,
    { width: '100px', height: '100px', background: 'green' });
  assert.equal(lossyOverall, false);
  assert.deepEqual(lossyReasons, []);
});
