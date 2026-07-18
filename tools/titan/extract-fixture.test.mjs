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
  selectorMatches,
  selectorMatchesPseudoElement,
  propsForElement,
  propsForBodyRoot,
  buildComponents,
  splitAnBOfSelector,
  collectDefinedTags,
  // wave-8: child combinator + support-asset inlining.
  splitSelectorChain,
  percentEncodeBytes,
  inlineUrlsInValue,
  inlineFixtureAssets,
  MAX_INLINE_ASSET_BYTES,
} from './extract-fixture.mjs';

// ── stripComments ───────────────────────────────────────────────────────────

test('stripComments removes HTML comments', () => {
  assert.equal(stripComments('a <!-- x --> b'), 'a  b');
});

test('stripComments removes CSS comments inside style', () => {
  assert.equal(stripComments('<style>/* hide */ p{color:red}</style>'),
                             '<style> p{color:red}</style>');
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
