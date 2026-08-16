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
  // wave-37 lane W8: the layered-cascade carve-out from "skip @-rules".
  layerCountOf,
  resolveLayeredCascade,
  propsForBodyRoot,
  buildComponents,
  // wave-17 BODY-HEIGHT SLOTTING: the explicit-absolute-height trigger.
  bodyDeclaresAbsoluteHeight,
  splitAnBOfSelector,
  collectDefinedTags,
  // wave-36 M7 BODY-ANCESTOR: the synthetic <html>/<body> scaffolding.
  DOCUMENT_SENTINEL_ANCESTORS,
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
  // wave-35 B7: the non-negative-delay stability window.
  iterationProgressAt,
  CAPTURE_WINDOW_MS,
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
  // wave-26 lane WWS: the inter-sibling whitespace marker.
  stampWsAfter,
  // wave-27 A-RC2: root-scope pseudo-element routing.
  isListItemDisplay,
  // wave-32 lane R: the ordered inline-content list (`_runs`).
  buildRunProto,
  alignRuns,
  // wave-33 lane N: the load-bearing unclosed-wrapper adoption (N1) and the
  // rightmost-unsupported merge-guard hole (N2).
  hasBoxAffectingInlineStyle,
  adoptsUnclosedBody,
  leadingTypeSelector,
  // wave-37 lane W4: THE LANG WIRE.
  documentLanguage,
  resolveLanguage,
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

// ── wave-37 lane W8: @layer / @scope descend instead of being skipped ───────

test('parseCss descends into @layer blocks and tags rules with layer order', () => {
  const rules = parseCss('@layer a{.x{color:red}}@layer b{.x{color:green}}.x{color:blue}');
  assert.deepEqual(rules.map((r) => r.selector), ['.x', '.x', '.x']);
  assert.equal(rules[0].layerIdx, 0);
  assert.equal(rules[1].layerIdx, 1);
  // The unlayered rule keeps the historical shape — no layer keys at all.
  assert.equal('layerIdx' in rules[2], false);
  assert.equal(layerCountOf(rules), 2);
});

test('parseCss honours a @layer statement for layer ORDER', () => {
  // layer-media-toggle.html: `@layer foo, bar;` fixes foo BEFORE bar even
  // though the `bar` block is written first.
  const rules = parseCss('@layer foo, bar;@layer bar{.x{color:green}}@layer foo{.x{color:red}}');
  assert.equal(rules[0].layerName, 'bar');
  assert.equal(rules[0].layerIdx, 1);
  assert.equal(rules[1].layerName, 'foo');
  assert.equal(rules[1].layerIdx, 0);
});

test('parseCss gives every anonymous @layer its own index', () => {
  const rules = parseCss('@layer{.x{color:red}}@layer{.x{color:green}}');
  assert.equal(rules[0].layerIdx, 0);
  assert.equal(rules[1].layerIdx, 1);
});

test('parseCss records !important per declaration', () => {
  const rules = parseCss('.a { color: red !important; width: 1px }');
  assert.equal(rules[0].props.color, 'red');
  assert.deepEqual(rules[0].important, { color: true });
});

test('parseCss still skips @media / @supports wholesale after the carve-out', () => {
  const rules = parseCss('@media print{.b{color:blue}}@supports (a:b){.c{color:teal}}.d{color:red}');
  assert.deepEqual(rules.map((r) => r.selector), ['.d']);
});

test('parseCss rewrites @scope selectors against the scope root', () => {
  const rules = parseCss('@scope (.test){input{color:green}:scope{color:red}:scope::before{content:"B"}}');
  assert.deepEqual(rules.map((r) => r.selector), ['.test input', '.test', '.test::before']);
});

test('parseCss unwraps an implicit @scope but declines its :scope rules', () => {
  // No static root exists for `@scope { … }` (it is the owning <style>'s
  // parent), so `:scope` rules are dropped rather than mis-targeted.
  const rules = parseCss('@scope{.a{color:green}:scope{color:red}}');
  assert.deepEqual(rules.map((r) => r.selector), ['.a']);
});

test('resolveLayeredCascade: later layer wins, unlayered beats every layer', () => {
  const out = resolveLayeredCascade([
    { rank: 0, props: { color: 'red' } },
    { rank: 1, props: { color: 'green' } },
    { rank: 2, props: { color: 'blue' } }, // rank === layerCount ⇒ unlayered
  ], 2);
  assert.equal(out.color, 'blue');
});

test('resolveLayeredCascade: important REVERSES layer order', () => {
  // css-cascade-5 §6.4.4 — an important declaration in the EARLIER layer
  // wins, and unlayered important is the lowest of the importants.
  const out = resolveLayeredCascade([
    { rank: 0, props: { color: 'green' }, important: { color: true } },
    { rank: 1, props: { color: 'red' }, important: { color: true } },
    { rank: 2, props: { color: 'blue' }, important: { color: true } },
  ], 2);
  assert.equal(out.color, 'green');
});

test('resolveLayeredCascade: revert-layer rolls back to the earlier layer', () => {
  const out = resolveLayeredCascade([
    { rank: 0, props: { color: 'green' } },
    { rank: 1, props: { color: 'revert-layer' } },
  ], 2);
  assert.equal(out.color, 'green');
});

test('resolveLayeredCascade: a revert-layer CHAIN walks back to the first layer', () => {
  // revert-layer-007.html — three layers each reverting the one before.
  const out = resolveLayeredCascade([
    { rank: 0, props: { color: 'green' } },
    { rank: 1, props: { color: 'revert-layer' } },
    { rank: 2, props: { color: 'revert-layer' } },
    { rank: 3, props: { color: 'revert-layer' } },
  ], 4);
  assert.equal(out.color, 'green');
});

test('resolveLayeredCascade: revert-layer with nothing earlier drops the property', () => {
  const out = resolveLayeredCascade([{ rank: 0, props: { color: 'revert-layer' } }], 1);
  assert.equal('color' in out, false);
});

test('resolveLayeredCascade: `all: revert-layer` reverts every declared property', () => {
  // revert-layer-003.html — one `all` line reverts width/height/background.
  const out = resolveLayeredCascade([
    { rank: 0, props: { width: '100px', 'background-color': 'green' } },
    { rank: 1, props: { width: '200px', 'background-color': 'red' } },
    { rank: 1, props: { all: 'revert-layer' } },
  ], 2);
  assert.deepEqual(out, { width: '100px', 'background-color': 'green' });
});

test('resolveLayeredCascade: the style attribute outranks every layer', () => {
  const out = resolveLayeredCascade([
    { rank: 0, props: { color: 'green' }, important: { color: true } },
    { rank: 2, props: { color: 'blue' }, important: { color: true }, inline: true },
  ], 1);
  assert.equal(out.color, 'blue');
});

test('resolveLayeredCascade: revert-layer in the style attribute falls back to the sheets', () => {
  // revert-layer-009 / -012: inline `background-color: revert-layer` rolls
  // the cascade back past the style attribute to the stylesheet value.
  const out = resolveLayeredCascade([
    { rank: 0, props: { color: 'green' } },
    { rank: 1, props: { color: 'revert-layer' }, inline: true },
  ], 0);
  assert.equal(out.color, 'green');
});

test('propsForElement resolves a layered cascade end-to-end', () => {
  // revert-layer-001.html in miniature.
  const rules = parseCss(
    '#target{width:100px}@layer{#target{background-color:green}}'
    + '@layer{#target{background-color:red;background-color:revert-layer}}',
  );
  const { props } = propsForElement(rules, 'div', { id: 'target' }, [], null, {});
  assert.equal(props['background-color'], 'green');
  assert.equal(props.width, '100px');
});

test('propsForElement keeps the historical path when no layer is involved', () => {
  const rules = parseCss('.a{color:red}.a{color:green}');
  const { props } = propsForElement(rules, 'div', { class: 'a' }, [], null, {});
  assert.equal(props.color, 'green');
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

test('bug1-fextractor: buildComponents omits _tag for the generic <div>', () => {
  // A bare <div class="t"> mirrors the shape of every visual-test fixture
  // — adding `_tag: "div"` here would balloon hundreds of existing fixtures
  // for zero renderer benefit (the renderer already defaults to div/Box).
  const html = '<body><div class="t"></div></body>';
  const { components } = buildComponents(html, parseCss(''), 'stem');
  assert.equal(components['stem__0']._tag, undefined);
});

// ── wave-31 lane S: `span` forwarding ──────────────────────────────────────
//
// <span> left GENERIC_WRAPPER_TAGS. A <div> is a block container and so is
// the renderers' default box, so withholding its tag costs nothing; a
// <span> is an INLINE box (css-display-3 §2.1) and the default box is not,
// so a surviving span rendered as a block <div> on web and destroyed the
// inline formatting context around it. The web harness TAG_ALLOWLIST has
// accepted 'span' since wave-26 — it just never received one.

test('lane-s: a surviving <span> carries _tag:"span"', () => {
  // `id` disqualifies the span from isPureInlineMergeable (rule 3), so it
  // survives as a component — the CSS2/abspos/static-inside-inline-001
  // shape, and the 274-test corpus population measured for this change.
  const html = '<body><span id="inline">X</span></body>';
  const { components } = buildComponents(html, parseCss(''), 'stem');
  assert.equal(components['stem__0']._tag, 'span');
  assert.equal(components['stem__0']._text, 'X');
});

test('lane-s: span forwarding does NOT change the inline merge', () => {
  // The merge machinery is untouched: a pure-inline, attribute-less,
  // un-styled <span> is still ABSORBED into the parent's `_text` and never
  // becomes a component, so it has no `_tag` to carry. Only spans that
  // survive the merge gained the field.
  const html = '<body><div class="t">a <span>b</span> c</div></body>';
  const { components } = buildComponents(html, parseCss(''), 'stem');
  assert.equal(components['stem__0']._text, 'a b c');
  assert.equal(components['stem__0'].children, undefined);
  // …and a styled span (rule 4: a rule targets the tag) survives WITH tag.
  const styled = buildComponents(
    '<body><div class="t">a <span>b</span></div></body>',
    parseCss('span { color: red }'),
    'stem',
  ).components;
  const kid = styled['stem__0'].children[Object.keys(styled['stem__0'].children)[0]];
  assert.equal(kid._tag, 'span');
});

test('lane-s: <div> stays the only generic wrapper', () => {
  // Regression pin for the set itself — if a future wave adds a member,
  // this test states the contract it has to satisfy: the tag is only
  // "generic" when the platform default box already renders it correctly.
  const html = '<body><div class="t"><span id="s">x</span></div></body>';
  const { components } = buildComponents(html, parseCss(''), 'stem');
  const outer = components['stem__0'];
  assert.equal(outer._tag, undefined, '<div> keeps no tag');
  const kid = outer.children[Object.keys(outer.children)[0]];
  assert.equal(kid._tag, 'span', '<span> now carries its tag');
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

// ── wave-36 M7 BODY-ANCESTOR: `body …` / `html …` scoping rules ────────────
//
// The component walker's ancestor chain starts INSIDE <body>, so a top-level
// component reached propsForElement with `ancestors = []`. Prepending only a
// `:root`-tagged sentinel meant `body` never appeared in any chain, and every
// rule whose non-rightmost compound is `body` — `body > div`, the single most
// common WPT scoping idiom (89 scored corpus tests, 48 of them failing) —
// matched nothing at all. css-logical/logical-values-float-clear-1 rendered
// its floats correctly inside a container that had silently lost
// `width: 20em; margin: 1em; padding: 2px; border: 1px solid silver`.

test('m7: the injected document chain is <html> then <body>, in document order', () => {
  // Shape pin — the matcher consumes the chain right-to-left, so `body` MUST
  // be last (the immediate parent of every top-level component).
  assert.deepEqual(DOCUMENT_SENTINEL_ANCESTORS.map((a) => a.tag), ['html', 'body']);
  // The root keeps the Selectors-4 §6.4.1 carve-out metadata.
  assert.equal(DOCUMENT_SENTINEL_ANCESTORS[0].pos.isRoot, true);
  // body is the 2nd of the document element's 2 element children
  // (HTML §13.2.6.4 always synthesises <head> + <body>), and the only body.
  assert.equal(DOCUMENT_SENTINEL_ANCESTORS[1].pos.sibIndex, 1);
  assert.equal(DOCUMENT_SENTINEL_ANCESTORS[1].pos.sibCount, 2);
  assert.equal(DOCUMENT_SENTINEL_ANCESTORS[1].pos.sibTypeCount, 1);
});

test('m7: `body > div` reaches a TOP-LEVEL component (the css-logical regression)', () => {
  // Verbatim from css/css-logical/logical-values-float-clear-1.html.
  const rules = parseCss(
    'body > div { width: 20em; margin: 1em; padding: 2px; border: 1px solid silver }',
  );
  const { props } = propsForElement(rules, 'div', { class: 'ltr' }, []);
  assert.equal(props.width, '20em');
  assert.equal(props.border, '1px solid silver');
});

test('m7: `body > div` does NOT reach a NESTED div (the child combinator still bites)', () => {
  // The float subject inside the container has a `div` parent, not `body`.
  const rules = parseCss('body > div { width: 20em }');
  const { props } = propsForElement(rules, 'div', { class: 'is' }, [{ tag: 'div', attrs: {} }]);
  assert.equal(props.width, undefined);
});

test('m7: `body span` and `html body p` descend through the synthetic chain', () => {
  const rules = parseCss('body span { color: green } html body p { color: blue }');
  assert.equal(propsForElement(rules, 'span', {}, []).props.color, 'green');
  assert.equal(propsForElement(rules, 'p', {}, []).props.color, 'blue');
  // …and one level deeper, where `body` is no longer the immediate parent.
  assert.equal(
    propsForElement(rules, 'span', {}, [{ tag: 'div', attrs: {} }]).props.color,
    'green',
  );
});

test('m7: `:root > x` correctly stops matching body children', () => {
  // Browser truth: the document element's only element children are <head>
  // and <body>, so `:root > div` matches nothing in the body subtree. The
  // pre-M7 single-sentinel chain wrongly made every top-level component a
  // child of :root. Descendant `:root div` must still match.
  const rules = parseCss(':root > div { color: red } :root div { color: green }');
  assert.equal(propsForElement(rules, 'div', {}, []).props.color, 'green');
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

test('wave8: splitSelectorChain rejects malformed chains', () => {
  // wave-30 A2 REVISED THIS PIN: `div + p` / `div ~ p` used to return null
  // ("sibling combinators unsupported"). They now tokenise — see the
  // wave-30 tests below. Only MALFORMED chains still bail.
  assert.equal(splitSelectorChain('> div'), null);     // leading child — malformed
  assert.equal(splitSelectorChain('div >'), null);     // trailing child — malformed
  assert.equal(splitSelectorChain('a >> b'), null);    // double child — malformed
  assert.equal(splitSelectorChain('div +'), null);     // trailing sibling — malformed
  assert.equal(splitSelectorChain('+ div'), null);     // leading sibling — malformed
  assert.equal(splitSelectorChain('a + ~ b'), null);   // doubled combinator
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
    // wave-30 A2 REVISED THIS PIN: sibling combinators now tokenise, so the
    // rightmost compound `b` IS the rule's host and must be guarded — the
    // pre-A2 code skipped the rule entirely and let <b> be merged away.
    'code + b { color: blue }',        // rightmost b → collected
  );
  const tags = collectStyledTags(rules);
  assert.deepEqual([...tags].sort(), ['b', 'div', 'em', 'span', 'strong']);
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

test('wave13 PIN: positive-delay and drifting zero-delay controls do NOT sample', () => {
  const kf = parseKeyframes(
    '@keyframes bgcolor { 0% { background-color: rgb(0, 200, 0); } 100% { background-color: rgb(200, 0, 0); } }');
  // Positive delay with NO fill: the whole capture window sits in the before
  // phase, where the effect value is the element's UNDERLYING value (Web
  // Animations §4.8.4.1) — unknowable statically.
  assert.equal(sampleKeyframesAnimation(
    { animation: 'bgcolor 1000000s cubic-bezier(0,1,1,0) 500000s' }, kf), null);
  // wave-35 B7 restated this leg. Zero delay is no longer refused per se —
  // this particular animation is refused because it genuinely DRIFTS: over
  // 100s the colour moves rgb(0, 200, 0) → rgb(1, 199, 0) inside the 1000 ms
  // capture window, so the two window ends disagree. (The zero-delay cases
  // that DO sample — with-images, contrast-color, rotate — are pinned below.)
  assert.equal(sampleKeyframesAnimation({ animation: 'bgcolor 100s' }, kf), null);
});

// ── wave-35 B7: the stability window (non-negative-delay sampling) ──────────
//
// Every pin below is the arithmetic of a REAL corpus test, named in its
// comment, and every admission is justified by the spec citation carried in
// extract-fixture.mjs's STABILITY WINDOW banner.

test('wave35 B7: @keyframes names accept the dashed-ident forms', () => {
  // css-animations-1 §4 types the name as <custom-ident>; css-values-4 §3.2 /
  // CSS Syntax 3 §4.3.11 admit a leading '-' and the '--' dashed prefix. The
  // measured case is css-color/animation/contrast-color-interpolation.html.
  assert.deepEqual(Object.keys(parseKeyframes('@keyframes --anim { from { opacity: 0 } }')), ['--anim']);
  assert.deepEqual(Object.keys(parseKeyframes('@keyframes -anim { from { opacity: 0 } }')), ['-anim']);
  assert.deepEqual(Object.keys(parseKeyframes('@keyframes plain { from { opacity: 0 } }')), ['plain']);
  // A bare digit run is not a valid identifier — still refused.
  assert.deepEqual(Object.keys(parseKeyframes('@keyframes 123 { from { opacity: 0 } }')), []);
});

test('wave35 B7: contrast-color() resolves to the higher-WCAG-contrast pole', () => {
  // css-color-5 §5: the function returns black or white, whichever contrasts
  // more with its argument (WCAG 2.1 relative luminance).
  assert.deepEqual(parseSrgbColor('contrast-color(white)'), { r: 0, g: 0, b: 0, a: 1 });
  assert.deepEqual(parseSrgbColor('contrast-color(black)'), { r: 255, g: 255, b: 255, a: 1 });
  assert.deepEqual(parseSrgbColor('contrast-color(yellow)'), { r: 0, g: 0, b: 0, a: 1 });
  assert.deepEqual(parseSrgbColor('contrast-color(navy)'), { r: 255, g: 255, b: 255, a: 1 });
  // An argument the sampler cannot parse refuses rather than guessing.
  assert.equal(parseSrgbColor('contrast-color(hsl(120, 50%, 50%))'), null);
});

test('wave35 B7: composite values interpolate component-wise', () => {
  // css-values-4 §7 componentwise interpolation; css-backgrounds-3 §5.4 types
  // box-shadow that way. The measured pair from contrast-color-interpolation.
  assert.equal(lerpCssValue('contrast-color(white) 100px 0px', 'lime 100px 0px', 0.5),
               'rgb(0, 128, 0) 100px 0px');
  // css-transforms/individual-transform/animation/individual-transform-combine
  // #div-2: `scale: 1 1` → `scale: 3 1` at 0.5.
  assert.equal(lerpCssValue('1 1', '3 1', 0.5), '2 1');
  // Differing component counts, and any single uninterpolable component,
  // refuse the WHOLE value — no half-interpolated composites.
  assert.equal(lerpCssValue('red 1px', 'blue 1px 2px', 0.5), null);
  assert.equal(lerpCssValue('red solid', 'blue dashed', 0.5), null);
  // A single paren-wrapped token is not a composite (path() stays out).
  assert.equal(lerpCssValue("path(nonzero, 'M0,0h1z')", "path(nonzero, 'M2,2h3z')", 0.5), null);
});

test('wave35 B7: iterationProgressAt implements the Web-Animations phases', () => {
  const base = { durationMs: 1000, delayMs: 2000, iterations: 1, fill: 'none', paused: false };
  // BEFORE phase without a backwards fill: no effect value (§4.8.4.1).
  assert.equal(iterationProgressAt(base, 0), null);
  assert.equal(iterationProgressAt({ ...base, fill: 'backwards' }, 0), 0);
  assert.equal(iterationProgressAt({ ...base, fill: 'both' }, 0), 0);
  // ACTIVE phase: progress is local time / duration.
  assert.equal(iterationProgressAt(base, 2500), 0.5);
  // AFTER phase without a forwards fill: no effect value (§4.8.4.3).
  assert.equal(iterationProgressAt(base, 9000), null);
  assert.equal(iterationProgressAt({ ...base, fill: 'forwards' }, 9000), 1);
  // `infinite` never reaches the after phase.
  assert.equal(iterationProgressAt({ ...base, iterations: Infinity }, 1e9), null); // p ≥ 1 → out of scope
  // paused collapses EVERY wall time to local time 0 (css-animations-1 §4.2 +
  // Web Animations §4.8.3.1) — the clip-path test's `-5s paused` shape.
  const paused = { durationMs: 10000, delayMs: -5000, iterations: 1, fill: 'none', paused: true };
  assert.equal(iterationProgressAt(paused, 0), 0.5);
  assert.equal(iterationProgressAt(paused, 60000), 0.5);
});

test('wave35 B7 PIN: css-color/animation/contrast-color-interpolation bakes green', () => {
  // `animation: --anim 2000s steps(2, start) both` over
  // `from { box-shadow: contrast-color(white) 100px 0px }` →
  // `to { box-shadow: lime 100px 0px }`.
  // steps(2, jump-start) outputs 0.5 for the WHOLE first half of the duration
  // (css-easing-1 §3.9.2), i.e. 1000 SECONDS — three orders of magnitude wider
  // than the capture window. contrast-color(white) = black, and black → lime
  // at 0.5 is rgb(0, 128, 0), which is exactly the `green` its reference
  // (/css/reference/ref-filled-green-100px-square-only.html) paints.
  const kf = parseKeyframes('@keyframes --anim { from { box-shadow: contrast-color(white) 100px 0px; }'
    + ' to { box-shadow: lime 100px 0px; } }');
  assert.deepEqual(sampleKeyframesAnimation({ animation: '--anim 2000s steps(2, start) both' }, kf), {
    baked: { 'box-shadow': 'rgb(0, 128, 0) 100px 0px' },
    dropped: ['animation'],
  });
});

test('wave35 B7 PIN: background-color-animation-with-images bakes the progress-0 blue', () => {
  // `animation: blue-anim 100s` (delay ZERO) over endpoints ONE 8-bit step
  // apart. Its committed reference bakes the static declaration
  // `background-color: rgb(0, 0, 199)` — the progress-0 value — so the
  // reftest itself asserts the t=0 answer.
  const kf = parseKeyframes('@keyframes blue-anim { 0% { background-color: rgb(0, 0, 199); }'
    + ' 100% { background-color: rgb(0, 0, 200); } }');
  assert.deepEqual(sampleKeyframesAnimation({ animation: 'blue-anim 100s' }, kf), {
    baked: { 'background-color': 'rgb(0, 0, 199)' },
    dropped: ['animation'],
  });
  // The SIBLING declaration in the very same test drifts in its SERIALIZED
  // form (the premultiplied lerp of two alpha-0 colours re-serializes to
  // `rgba(0, 0, 0, 0)`), so it is refused. Byte identity is deliberately
  // stricter than render identity: refusing costs nothing (the verbatim path
  // renders the same transparent box) while guessing could not be undone.
  const kfT = parseKeyframes('@keyframes t { 0% { background-color: rgba(0, 200, 0, 0); }'
    + ' 100% { background-color: rgba(200, 0, 0, 0); } }');
  assert.equal(sampleKeyframesAnimation({ animation: 't 100s' }, kfT), null);
});

test('wave35 B7 PIN: identical endpoints and from,to lists bake; fill spans the window', () => {
  // css-transforms/animation/rotate-animation-with-will-change-transform-001:
  // `animation: a linear 10s infinite` over `from`/`to` both `0 1 0 44deg`.
  const kfRot = parseKeyframes('@keyframes a { from { rotate: 0 1 0 44deg; } to { rotate: 0 1 0 44deg; } }');
  assert.deepEqual(sampleKeyframesAnimation({ animation: 'a linear 10s infinite' }, kfRot).baked,
                   { rotate: '0 1 0 44deg' });
  // A 1s `both`-filled animation: t=0 is the active phase at progress 0 and
  // t=CAPTURE_WINDOW_MS is the after phase at progress 1 — DIFFERENT phases,
  // same value, because `from, to` declares one value at both offsets.
  // (css-animations/animation-name-in-nested-shadow's shape.)
  assert.equal(CAPTURE_WINDOW_MS, 1000);
  const kfBoth = parseKeyframes('@keyframes doc { from, to { background-color: lightgreen } }');
  assert.deepEqual(sampleKeyframesAnimation(
    { 'animation-name': 'doc', 'animation-duration': '1s', 'animation-fill-mode': 'both' }, kfBoth).baked,
    { 'background-color': 'lightgreen' });
  // Drop the fill and the after-phase end has NO effect value → refuse.
  assert.equal(sampleKeyframesAnimation(
    { 'animation-name': 'doc', 'animation-duration': '1s' }, kfBoth), null);
});

test('wave35 B7 PIN: contain-animation-001 still refuses (non-animatable beats the new path)', () => {
  // The wave-27 A-RC3 hole this closed: a paused, ZERO-delay animation now
  // clears the delay boundary, so KEYFRAME_NON_ANIMATABLE is the ONLY thing
  // keeping `contain: none` out of the fixture. It holds.
  const kf = parseKeyframes('@keyframes bad { from { contain: none; } }');
  assert.equal(sampleKeyframesAnimation(
    { 'animation-duration': '1s', 'animation-name': 'bad', 'animation-play-state': 'paused' }, kf), null);
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
  // wave-32 lane R: the flag's JOB is done here — `_runs` now carries the
  // true order, so the component ships the wire instead of the marker.
  const css = 'span { color: red }';
  const { components, lossyReasons } = buildComponents(
    '<body><p>the quick <span>brown</span> fox</p></body>', parseCss(css), 'r');
  const p = components['r__0'];
  // `_text` is UNCHANGED — the concatenation stays on the wire for readers
  // that drop meta, which is what makes `_runs` additive.
  assert.equal(p._text, 'the quick fox');
  // The order the browser paints, now expressible:
  assert.deepEqual(p._runs, [
    { text: 'the quick ' },
    { child: 'r__0__0' },
    { text: ' fox' },
  ]);
  // …and the word spaces either side of the child SURVIVE (they are
  // interior to the sequence, so only its outer edges were trimmed).
  assert.equal(p._runs[0].text.endsWith(' '), true);
  assert.equal(p._runs[2].text.startsWith(' '), true);
  // Reason retired, at BOTH levels — nothing is being approximated now.
  assert.equal((p._lossyReasons ?? []).includes('inline-run-reordered'), false);
  assert.equal(lossyReasons.includes('inline-run-reordered'), false);
  // The referenced key is the child's map key, i.e. its `name` after the
  // converter hop (the converter mints ids, so an id would dangle there).
  assert.ok(Object.keys(p.children).includes('r__0__0'));
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

test('B-RC9a: a VOID child (<br>) takes its position in the run list too', () => {
  // wave-32 lane R: a <br> is a component (the wave-21 B-RC1 line-box
  // spacer), so it occupies a slot in the inline flow exactly like a
  // paired child — and the two text runs are on either side of it, which
  // is the whole reason `_text` read 'line1line2' before.
  const { components } = buildComponents(
    '<body><p>line1<br>line2</p></body>', [], 'v');
  const p = components['v__0'];
  assert.equal(p._text, 'line1line2');
  assert.deepEqual(p._runs, [
    { text: 'line1' },
    { child: 'v__0__0' },
    { text: 'line2' },
  ]);
  assert.equal((p._lossyReasons ?? []).includes('inline-run-reordered'), false);
});

// ── wave-32 lane R: the reorder bail LIFTED ────────────────────────────────
//
// Wave-31 pinned the honesty contract here and wrote: "If a future wave
// lands `_runs`, these assertions are the ones that get to flip." This is
// that wave, and these are those assertions, flipped: the shape that used
// to lose the order now carries it, on the exact markup of the CSS2 test
// that motivated the lane. What did NOT change is the honesty rule itself —
// the reason is retired per component and ONLY when `_runs` actually
// emitted; the alignment-refusal test below keeps the bail pinned for the
// population the new wire cannot prove itself on.

test('lane-r: the CSS2 static-inside-inline shape now ships the true order', () => {
  // CSS2/abspos/static-inside-inline-001 verbatim: an out-of-flow div
  // authored BEFORE the span's text. The div is styled (`#abspos`) so it
  // survives as a child; 'X' lands in `_text` and paints first, which
  // flips the abspos' static position — the quantity the test asserts
  // (§10.6.4 over the §9.4.2 zero-height line box).
  const css = '#abspos { position: absolute; width: 100px; height: 100px }';
  const { components, lossyReasons } = buildComponents(
    '<body><div id="wrapper"><span id="inline"><div id="abspos"></div> X </span></div></body>',
    parseCss(css), 'css2');
  const wrapper = components['css2__0'];
  const span = wrapper.children[Object.keys(wrapper.children)[0]];
  // The span survived the merge (it has an id) and carries its tag…
  assert.equal(span._tag, 'span');
  // …`_text` still reads 'X' (the fallback channel is untouched)…
  assert.equal(span._text, 'X');
  // …but `_runs` now says what the browser paints: the abspos BOX first,
  // then the text. That is the quantity the WPT test asserts (§10.6.4 over
  // the §9.4.2 zero-height line box), and we were flipping it.
  const spanKey = Object.keys(wrapper.children)[0];
  assert.deepEqual(span._runs, [
    { child: `${spanKey}__0` },
    { text: ' X' },
  ]);
  // Both honesty levels are clean — the approximation is gone, not hidden.
  assert.equal((span._lossyReasons ?? []).includes('inline-run-reordered'), false);
  assert.equal(lossyReasons.includes('inline-run-reordered'), false);
});

test('lane-r: an out-of-flow reorder is expressed, not downgraded', () => {
  // The narrow "all preceding kept children are out-of-flow" family (6
  // components corpus-wide) is where the in-flow paint order genuinely
  // does NOT move — only the static position does. Wave-31 refused to call
  // that lossless while no renderer could place the box; wave-32 places it,
  // so the list is emitted here exactly as it is for an in-flow reorder.
  // The RULE is unchanged: expressible → wire; not expressible → marker.
  const css = '.abs { position: absolute }';
  const { components } = buildComponents(
    '<body><p><span class="abs"></span>tail</p></body>', parseCss(css), 'oof');
  const p = components['oof__0'];
  assert.equal(p._text, 'tail');
  assert.deepEqual(p._runs, [{ child: 'oof__0__0' }, { text: 'tail' }]);
  assert.equal((p._lossyReasons ?? []).includes('inline-run-reordered'), false);
});

// ── wave-32 lane R: the run list's own contract ────────────────────────────

test('lane-r: no interleave → no `_runs` key at all (the additive guarantee)', () => {
  // The corpus stays byte-identical outside the interleaving population
  // BECAUSE of this: a component whose own text and children do not meet
  // gains nothing. Three shapes that must all stay bare.
  //
  // wave-34 lane R: case (a) — text BEFORE a kept child — MOVED OUT of this
  // list and into its own test below. It is an interleave (the text ends at
  // an element boundary, not at the end of the line), and shipping only
  // `_text` there deletes the boundary space. The additive guarantee itself
  // is unchanged: the shapes here still gain nothing.
  const css = 'u { color: red }';
  // (a) merge-absorbed run — spliced IN PLACE, so there is no element
  //     boundary inside the content at all.
  const merged = buildComponents(
    '<body><p>square and <strong>no red</strong>.</p></body>', [], 'b').components['b__0'];
  assert.equal(merged._runs, undefined);
  // (b) plain leaf text, no children at all.
  const leaf = buildComponents('<body><p>just text</p></body>', [], 'c').components['c__0'];
  assert.equal(leaf._runs, undefined);
  // (c) children with NO own text — the only own text is the inter-sibling
  //     newline+indent, which normalises away. That boundary belongs to the
  //     wave-26 `ws-after` channel; emitting it here too would double the
  //     space on any renderer honouring both.
  const wsOnly = buildComponents(
    '<body><p>\n  <u>a</u>\n  <u>b</u>\n</p></body>', parseCss(css), 'd').components['d__0'];
  assert.equal(wsOnly._text, undefined);
  assert.equal(wsOnly._runs, undefined);
});

test('lane-r: text-then-child DOES interleave — the boundary space rides the list', () => {
  // wave-34 lane R, the wave-33 lane N follow-up made good. `<p>…green
  // <em>…</em></p>` paints in document order, so wave-32's reorder-only gate
  // shipped no list — and `_text`'s CSS Text §4.1 trim then deleted the space
  // between 'quick' and the child, welding two words the browser separates.
  // §4.1 collapses that space to ONE space; it never deletes it. The trim is
  // only correct at the END OF THE CONTENT, and here the content continues.
  const css = 'u { color: red }';
  const { components } = buildComponents(
    '<body><p>the quick <u>brown</u></p></body>', parseCss(css), 'a');
  const p = components['a__0'];
  // `_text` is untouched — the fallback channel still carries the trimmed
  // concatenation, so a reader that ignores `_runs` behaves exactly as it
  // did before the key existed (that is what makes the wire additive).
  assert.equal(p._text, 'the quick');
  // …and the list carries the space the trim ate.
  assert.deepEqual(p._runs, [{ text: 'the quick ' }, { child: 'a__0__0' }]);
  // No reorder happened, so no reorder marker is retired — there was never
  // one to retire. The two facts stay independent.
  assert.equal((p._lossyReasons ?? []).includes('inline-run-reordered'), false);
});

test('lane-r: a whitespace-only run between two children survives', () => {
  // spec 03 §4.1 rule 6 — that space IS the inter-run word space and has
  // real advance width in the ref; collapsing it away is how the flat wire
  // used to pack inline-block boxes 4.5px too far left (the wave-26 ws-after
  // measurement). Only the sequence's OUTER edges are trimmed.
  const css = 'u { color: red }';
  const { components } = buildComponents(
    '<body><p>  <u>a</u> <u>b</u> tail</p></body>', parseCss(css), 'w');
  const p = components['w__0'];
  assert.deepEqual(p._runs, [
    // The LEADING piece was whitespace-only and sits at the sequence's
    // outer edge, so the §4.1 trim emptied it and it is dropped…
    { child: 'w__0__0' },
    // …while the INTERIOR one is the word space between the two boxes and
    // is kept verbatim.
    { text: ' ' },
    { child: 'w__0__1' },
    { text: ' tail' },
  ]);
});

test('lane-r: buildRunProto trims only the ENDS of the sequence', () => {
  // The unit under test is the normalisation rule, isolated from the walk:
  // per-piece collapse, sequence-edge trim. Trimming each piece would eat
  // two word spaces here and silently re-join words the browser separates.
  const proto = buildRunProto([
    { t: 'text', raw: '\n  the quick ' },
    { t: 'el', tag: 'u' },
    { t: 'text', raw: ' fox \n ' },
  ]);
  assert.deepEqual(proto, [
    { text: 'the quick ' },
    { el: 0, tag: 'u' },
    { text: ' fox' },
  ]);
});

test('lane-r: buildRunProto keeps the pre family verbatim', () => {
  // CSS Text §4.1.1: under `white-space: pre` neither the collapse nor the
  // trim applies — the same exemption scanOwnText's buffer path takes.
  const proto = buildRunProto([
    { t: 'text', raw: '  a\tb\n' },
    { t: 'el', tag: 'span' },
  ], true);
  assert.deepEqual(proto, [{ text: '  a\tb\n' }, { el: 0, tag: 'span' }]);
});

test('lane-r: buildRunProto decodes references per piece', () => {
  // Decoding runs AFTER the tag/text split and BEFORE the white-space step,
  // per piece — a reference can never span a child element, so this agrees
  // byte for byte with the whole-buffer decode.
  const proto = buildRunProto([
    { t: 'text', raw: 'a&amp;b ' },
    { t: 'el', tag: 'br' },
    { t: 'text', raw: ' &#9;c' },
  ]);
  assert.deepEqual(proto, [{ text: 'a&b ' }, { el: 0, tag: 'br' }, { text: ' c' }]);
});

test('lane-r: alignRuns REFUSES when the two walks disagree (bail preserved)', () => {
  // The proof, not an assumption — scanOwnText and the tree walker are
  // different code, and a list that names the wrong box silently reorders
  // content instead of loudly approximating it.
  const proto = [{ text: 'a' }, { el: 0, tag: 'u' }, { text: 'b' }];
  // Count mismatch (maxDepth truncation / head-only sibling dropped).
  assert.equal(alignRuns(proto, []), null);
  // Tag mismatch at a position (both kept one element, not the same one).
  assert.equal(alignRuns(proto, [{ tag: 'span' }]), null);
  // Agreement → the positional entries become child indices.
  assert.deepEqual(alignRuns(proto, [{ tag: 'u' }]), [
    { text: 'a' }, { childIndex: 0 }, { text: 'b' },
  ]);
  // A single-entry list says nothing `_text` does not already say.
  assert.equal(alignRuns([{ el: 0, tag: 'u' }], [{ tag: 'u' }]), null);
});

test('lane-r: a refused alignment KEEPS the wave-21 marker', () => {
  // The reachable refusal in the live pipeline: a HEAD_ONLY tag inside the
  // body. `recurse` drops it before the merge filter, so it never becomes a
  // child, while scanOwnText (which knows only the mergeable predicate)
  // counts it as one. The walks disagree, alignRuns refuses — and the
  // component must then ship the wave-21 bail, unchanged. Retirement is per
  // component and only when EARNED.
  const { components, lossyReasons } = buildComponents(
    '<body><p>lead <style>i{color:red}</style> tail</p></body>', [], 'd');
  const p = components['d__0'];
  assert.equal(p._runs, undefined);
  // CSS text never leaks into `_text`; the two prose runs glued instead.
  assert.equal(p._text, 'lead tail');
  assert.ok(p._lossyReasons.includes('inline-run-reordered'));
  assert.ok(lossyReasons.includes('inline-run-reordered'));
});

test('lane-r: a collapsed inline chain drops the run list with the children', () => {
  // The wave-22 collapse flattens the subtree into ONE run and empties
  // `children`, so any list computed for it names boxes that no longer
  // exist. The two features can therefore never co-occur, which is why
  // `_decorations` and `_runs` need no precedence rule between them.
  const css = 'u { text-decoration: underline }';
  const { components } = buildComponents(
    '<body><p>the quick <u>brown</u> fox</p></body>', parseCss(css), 'e');
  const p = components['e__0'];
  assert.equal(p._runs, undefined);
  assert.equal(p._text, 'the quick brown fox');
  assert.ok(p._decorations);
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

// ── wave-26 lane WWS: the inter-sibling whitespace marker ────────────────────
//
// Contract under test (see extract-fixture.mjs's WS_AFTER_ROLE banner):
// walkChildren records whether COLLAPSIBLE source whitespace separated two
// adjacent elements; buildComponents replays that fact as `_role:
// 'ws-after'` on the EARLIER sibling, and the web harness consumes it as
// `meta.role` to re-insert one real ' ' text node. The tests below pin the
// three properties the honesty argument rests on: it fires when whitespace
// was there, it NEVER fires when it wasn't, and it never claims the last
// sibling (whose trailing gap separates nothing).

test('wave26 WWS: whitespace-separated siblings carry the ws-after marker', () => {
  // backdrop-filter-clip-rect-2's exact shape — three inline-block boxes,
  // one per source line, inside a block container. The ref collapses each
  // newline+indent to one space advance; without the marker the composed
  // canvas packed them flush and boxes 2/3 landed 4 and 8 px left.
  const css = '.box { display: inline-block; width: 100px; height: 100px; }';
  const html = '<!DOCTYPE html><style>' + css + '</style><body><div class="row">\n'
    + '  <div class="box"></div>\n'
    + '  <div class="box"></div>\n'
    + '  <div class="box"></div>\n'
    + '</div></body>';
  const { components } = buildComponents(html, parseCss(css), 'ws');
  const kids = components['ws__0'].children;
  // Boxes 0 and 1 have a following sibling AND source whitespace after them.
  assert.equal(kids['ws__0__0']._role, 'ws-after');
  assert.equal(kids['ws__0__1']._role, 'ws-after');
  // Box 2 is last — its trailing newline separates it from a close tag.
  assert.equal(kids['ws__0__2']._role, undefined);
});

test('wave26 WWS: flush-authored siblings carry NO marker', () => {
  // The anti-invention pin. Source packed the boxes with no whitespace, so
  // the wire must say so and the renderer must keep them flush — this is
  // the property that makes the harness extension safe to turn on.
  const css = '.box { display: inline-block; width: 100px; height: 100px; }';
  const html = '<!DOCTYPE html><style>' + css + '</style>'
    + '<body><div class="row"><div class="box"></div><div class="box"></div></div></body>';
  const { components } = buildComponents(html, parseCss(css), 'flush');
  const kids = components['flush__0'].children;
  assert.equal(kids['flush__0__0']._role, undefined);
  assert.equal(kids['flush__0__1']._role, undefined);
});

test('wave26 WWS: non-breaking space is NOT a separator', () => {
  // CSS Text §4.1 lists exactly five collapsible characters; U+00A0 is not
  // one of them — it is real text with its own advance. Marking it would
  // double the gap, so the walker's [ \t\n\r\f] class must reject it.
  const css = '.box { display: inline-block; width: 10px; height: 10px; }';
  const html = '<!DOCTYPE html><style>' + css + '</style>'
    + '<body><div class="row"><div class="box"></div> <div class="box"></div></div></body>';
  const { components } = buildComponents(html, parseCss(css), 'nb');
  assert.equal(components['nb__0'].children['nb__0__0']._role, undefined);
});

test('wave26 WWS: body-level siblings are marked too', () => {
  // Top-level elements become real siblings under the body-root on the
  // wave-17 slotting path, where the renderer's separator hook does pair
  // them — so the same source fact must reach the same wire shape.
  const css = 'span { display: inline-block; width: 10px; height: 10px; }';
  const html = '<!DOCTYPE html><style>' + css + '</style>'
    + '<body><span></span>\n<span></span></body>';
  const { components } = buildComponents(html, parseCss(css), 'top');
  assert.equal(components['top__0']._role, 'ws-after');
  assert.equal(components['top__1']._role, undefined);
});

test('wave26 WWS: stampWsAfter never overwrites an existing role', () => {
  // Stated precedence (banner): 'line-break' — the only role that can
  // collide — wins, and the caller learns the marker was withheld from the
  // return value instead of the decision being invisible.
  const br = { properties: {}, _role: 'line-break' };
  assert.equal(stampWsAfter(br, { wsAfter: true }, true), null);
  assert.equal(br._role, 'line-break');
});

test('wave26 WWS: stampWsAfter is a no-op without a following sibling', () => {
  // A gap needs two sides. Trailing whitespace before the parent's close
  // tag separates nothing, so the last sibling stays unmarked.
  const last = { properties: {} };
  assert.equal(stampWsAfter(last, { wsAfter: true }, false), null);
  assert.equal(last._role, undefined);
  // …and a marked node with a sibling does get stamped, so the guard above
  // is proving the gate and not merely that the helper never fires.
  const mid = { properties: {} };
  assert.equal(stampWsAfter(mid, { wsAfter: true }, true), 'ws-after');
  assert.equal(mid._role, 'ws-after');
});

test('wave26 WWS: nested tree nodes carry the walker fact', () => {
  // extractBodyTreeNested is the shared tree the fixture emitter reads;
  // pin the flag there too so a future refactor of buildComponents cannot
  // silently lose the signal between the walker and the wire.
  const tree = extractBodyTreeNested('<body><div><b>a</b> <i>b</i><u>c</u></div></body>');
  const kids = tree[0].children;
  assert.equal(kids[0].wsAfter, true);   // <b> … whitespace … <i>
  assert.equal(kids[1].wsAfter, undefined); // <i><u> written flush
});

// ── wave-27 A-RC2: root-scope pseudo-element rules stop leaking ─────────────
//
// `parseCompound` accepts `html::before` (needTag 'html', pseudoElement
// 'before'), and propsForBodyRoot's acceptance tests all keyed on the TAG
// alone — so the rule's declarations were `Object.assign`-ed straight into
// the BODY's own bag. Two wrongs at once, both MEASURED on css-contain's
// contain-body-dir / contain-body-w-m families and css-writing-modes'
// wm-propagation family (26 bucket-A tests):
//   1. the GENERATED box was lost (no `_pseudo` bucket existed for the root),
//   2. its declarations CLOBBERED the body's (`background: orange` became the
//      body's own background, which then flooded the canvas through the
//      body→canvas propagation channel wave-27 A-RC1 gates).
// Per CSS Generated Content L3 §3.2 `content` is only honoured ON a
// pseudo-element, and per Selectors-4 §3.3 a `::pseudo` rule styles the
// generated box, never its originating element.

test('wave27 A-RC2: html::before goes to the pseudo bucket, not the body bag', () => {
  // contain-body-dir-001's exact stylesheet, trimmed to the two rules.
  const rules = parseCss(
    'html::before { content: ""; width: 100px; height: 100px;' +
    ' background: orange; display: block }' +
    ' body { width: 200px; height: 200px; direction: rtl; contain: layout }'
  );
  const { props, pseudo } = propsForBodyRoot(rules);
  // The body's OWN declarations survive untouched — and nothing else.
  assert.deepEqual(props, {
    width: '200px', height: '200px', direction: 'rtl', contain: 'layout',
  });
  // The generated box's declarations land whole in the `before` bucket,
  // with their own 100px geometry intact (the body's 200px no longer
  // clobbers them, and `background: orange` no longer clobbers the body).
  assert.deepEqual(pseudo.before, {
    content: '""', width: '100px', height: '100px',
    background: 'orange', display: 'block',
  });
});

test('wave27 A-RC2: ::before and ::after get separate buckets', () => {
  // css-writing-modes/wm-propagation-002 declares both on html.
  const rules = parseCss(
    'html::before { content: "a" } html::after { content: "b" } body { margin: 0 }'
  );
  const { props, pseudo } = propsForBodyRoot(rules);
  assert.deepEqual(props, { margin: '0' });
  assert.equal(pseudo.before.content, '"a"');
  assert.equal(pseudo.after.content, '"b"');
});

test('wave27 A-RC2: same-pseudo rules cascade last-write-wins', () => {
  // Same cascade contract propsForElement's pseudo bucket already has.
  const rules = parseCss(
    ':root::before { content: "a"; color: red } html::before { color: blue }'
  );
  const { pseudo } = propsForBodyRoot(rules);
  assert.deepEqual(pseudo.before, { content: '"a"', color: 'blue' });
});

test('wave27 A-RC2: a bare ::marker is dropped unless the root is a list item', () => {
  // css-lists / css-pseudo author `::marker { … }` with no tag — a UNIVERSAL
  // marker rule. propsForElement already attaches it to every real <li>;
  // hanging it on the html/body root too would paint a phantom marker,
  // because css-lists-3 §3.1 only generates a ::marker box for a box with
  // `display: list-item`. So at ROOT scope it is dropped (31 bucket-A tests).
  const dropped = propsForBodyRoot(parseCss('::marker { font-family: monospace }'));
  assert.equal(dropped.pseudo.marker, undefined);
  assert.deepEqual(dropped.props, {});     // and it never reaches the flat bag
  // …but a root that IS a list item keeps it, so the rule is a real gate.
  const kept = propsForBodyRoot(parseCss(
    'body { display: list-item } ::marker { font-family: monospace }'
  ));
  assert.deepEqual(kept.pseudo.marker, { 'font-family': 'monospace' });
});

test('wave27 A-RC2: isListItemDisplay accepts the two-value display syntax', () => {
  // css-display-3 §2 spells the same box as `block flow list-item` etc.
  assert.equal(isListItemDisplay('list-item'), true);
  assert.equal(isListItemDisplay('BLOCK FLOW LIST-ITEM'), true);
  assert.equal(isListItemDisplay('inline list-item'), true);
  // Everything else — including the html/body initial value and absence.
  assert.equal(isListItemDisplay('block'), false);
  assert.equal(isListItemDisplay(undefined), false);
  assert.equal(isListItemDisplay(''), false);
});

test('wave27 A-RC2: the body-root component carries the root _pseudo bucket', () => {
  // End-to-end through buildComponents: the generated box must reach the
  // fixture as a real `_pseudo` entry (the SAME wire shape the per-element
  // path emits), so the converter's `_pseudo` → v2 `pseudos` rename and all
  // three renderers' pseudo-span paths render it with no new code.
  const html = '<body><p>hi</p></body>';
  const rules = parseCss(
    'html::before { content: ""; background: orange; display: block }' +
    ' body { direction: rtl }'
  );
  const { components } = buildComponents(html, rules, 't');
  const body = components['t__body'];
  assert.deepEqual(body.properties, { direction: 'rtl' });
  assert.deepEqual(body._pseudo.before.properties, {
    content: '""', background: 'orange', display: 'block',
  });
  assert.equal(body._role, 'body-root');
});

test('wave27 A-RC2: a pseudo-only root still emits the body-root', () => {
  // The pre-A-RC2 emit gate looked at the flat bag alone, which was only
  // sound while pseudo declarations were (wrongly) merged into it. A
  // document whose ONLY root-scope rule is a pseudo one still needs the
  // synthetic root — it is the box the generated content hangs off.
  const { components } = buildComponents(
    '<body><p>hi</p></body>', parseCss('html::before { content: "x" }'), 't'
  );
  assert.deepEqual(components['t__body'].properties, {});
  assert.equal(components['t__body']._pseudo.before.properties.content, '"x"');
});

test('wave27 A-RC2: a root without pseudo rules emits no _pseudo key', () => {
  // Omit-when-empty, exactly like the per-element path — every pre-wave-27
  // fixture without root pseudo rules stays byte-identical.
  const { components } = buildComponents(
    '<body><p>hi</p></body>', parseCss('body { color: red }'), 't'
  );
  assert.equal(components['t__body']._pseudo, undefined);
});

// ── wave-27 A-RC3: non-animatable keyframe declarations are ignored ─────────
//
// css-animations-1 §4: "Properties that aren't animatable are ignored in
// these rules, with the exception of `animation-timing-function` …".
// MEASURED (css-contain/contain-animation-001): the test declares
// `div { contain: strict; animation: … paused }` with
// `@keyframes bad { from { contain: none } }` and asserts in its own
// <meta name=assert> that "the contain property is not animatable" — its ref
// is a plain 100px green square. That test survives today only by accident
// (its animation-delay is 0, so the sampler's strictly-negative-delay
// boundary rejects it first); give it a negative delay and the sampler would
// bake `contain: none` into the fixture, deleting the containment under test.

test('wave27 A-RC3: a keyframes block of only non-animatable props never samples', () => {
  // contain-animation-001 with the one guard removed (negative delay), so
  // ONLY the new rule can stop the bake.
  const kf = parseKeyframes('@keyframes bad { from { contain: none } }');
  const props = {
    contain: 'strict', 'animation-name': 'bad',
    'animation-duration': '1s', 'animation-delay': '-0.5s',
    'animation-play-state': 'paused',
  };
  // null = "out of scope, extract verbatim" — i.e. the STATIC cascade, which
  // is exactly what the browser paints for a non-animatable property.
  assert.equal(sampleKeyframesAnimation(props, kf), null);
});

test('wave27 A-RC3: animatable siblings still bake around an ignored one', () => {
  // §4 ignores only the non-animatable DECLARATIONS, not the whole block:
  // `opacity` must still sample while `contain` is dropped.
  const kf = parseKeyframes(
    '@keyframes mix { from { contain: none; opacity: 0 } to { contain: strict; opacity: 1 } }'
  );
  const out = sampleKeyframesAnimation({
    'animation-name': 'mix', 'animation-duration': '1s',
    'animation-delay': '-0.5s', 'animation-timing-function': 'linear',
  }, kf);
  assert.deepEqual(Object.keys(out.baked), ['opacity']);
  assert.equal(out.baked.opacity, '0.5');
});

test('wave27 A-RC3: animation-* declarations inside a keyframe are ignored', () => {
  // The same §4 paragraph's other half. animation-timing-function is the
  // spec's ONE exception and never reaches the set — parseKeyframeBody
  // lifts it into the frame's `easing` before the sampler sees it.
  const kf = parseKeyframes(
    '@keyframes a { from { animation-duration: 2s } to { animation-duration: 3s } }'
  );
  assert.equal(sampleKeyframesAnimation({
    'animation-name': 'a', 'animation-duration': '1s', 'animation-delay': '-0.5s',
  }, kf), null);
});

test('wave27 A-RC3: contain-animation-001 extracts with its static contain', () => {
  // The end-to-end shape: the fixture keeps `contain: strict` from the
  // cascade and the keyframe value never appears.
  const rules = parseCss(
    'div { contain: strict; animation-duration: 1s; animation-name: bad;' +
    ' animation-play-state: paused }'
  );
  const kf = parseKeyframes('@keyframes bad { from { contain: none } }');
  const { components } = buildComponents('<body><div>x</div></body>', rules, 't', null, kf);
  assert.equal(components['t__0'].properties.contain, 'strict');
});

// ── wave-27 lane CBAKE: the LIST-ORDINAL attr lane ──────────────────────────
//
// `<ol start>` / `<li value>` ride the same `_attrs` envelope as the wave-20
// widget attributes but through a DISJOINT tag+key set, so the web renderer
// can paint native list numbering. Pinned at both altitudes: the pure
// widgetAttrsFor table and the buildComponents emission beside `_tag`.

test('cbake: widgetAttrsFor — ol/li forward start/value as verbatim strings', async () => {
  const { widgetAttrsFor } = await import('./extract-fixture.mjs');
  // `start` is the ordered-list counter origin (HTML §4.4.5).
  assert.deepEqual(widgetAttrsFor('ol', { start: '1860' }), { start: '1860' });
  // Numeric-looking but NOT coerced — the DOM re-parses it, and keeping the
  // lane string-only avoids a second numeric-typing rule on the wire.
  assert.equal(typeof widgetAttrsFor('ol', { start: '10' }).start, 'string');
  // `value` is the per-item ordinal override (HTML §4.4.8).
  assert.deepEqual(widgetAttrsFor('li', { value: '4' }), { value: '4' });
});

test('cbake: the list lane is allow-listed and omit-when-empty', async () => {
  const { widgetAttrsFor } = await import('./extract-fixture.mjs');
  // Selector fuel (id/class/style) and non-lane attributes never forward.
  // (wave-44: `reversed` joined the lane as a presence-boolean, so the
  // bare `reversed` here now forwards as literal `true` — see the U5 block.)
  assert.deepEqual(widgetAttrsFor('ol', { start: '3', id: 'x', type: 'a', reversed: '' }),
    { start: '3', reversed: true });
  // No qualifying attribute at all → no `_attrs` field at all.
  assert.equal(widgetAttrsFor('ol', { id: 'x' }), null);
  assert.equal(widgetAttrsFor('li', {}), null);
  // The widget lane is unreachable from a list tag: `checked` would be a
  // presence-boolean on an <input> but is simply not in the list lane.
  assert.equal(widgetAttrsFor('li', { checked: '' }), null);
});

test('cbake: the two lanes stay disjoint', async () => {
  const { widgetAttrsFor, WIDGET_ATTR_TAGS, LIST_ATTR_TAGS } =
    await import('./extract-fixture.mjs');
  // No tag may claim both lanes — the whole point of the separate sets
  // (WIDGET_TAGS also drives inert/tabIndex and the inter-sibling space).
  for (const t of LIST_ATTR_TAGS) assert.equal(WIDGET_ATTR_TAGS.has(t), false, t);
  // `start` on a widget tag is not forwarded (it is not a widget attribute).
  assert.equal(widgetAttrsFor('input', { start: '3' }), null);
});

test('cbake: buildComponents emits `_attrs` beside `_tag` for <ol start>', () => {
  const { components } = buildComponents(
    "<body><ol start='1860'><li>x</li></ol></body>", parseCss('ol { color: red }'), 't');
  const ol = components['t__0'];
  assert.equal(ol._tag, 'ol');
  assert.deepEqual(ol._attrs, { start: '1860' });
});

// ═════════════════════════════════════════════════════════════════════════════
// wave-29 lane SELECTION: two extraction defects (S-RC1 PHANTOM BODY, S-RC2)
// ═════════════════════════════════════════════════════════════════════════════

// New exports under test — dynamic import, same pattern as the wave-21 block.
const {
  maskNonMarkupForBodyScan,
  isBeforeBodyPrefix,
} = await import('./extract-fixture.mjs');

// ── S-RC1: the phantom-body hole (locateBodyContent step 1.5) ───────────────

// The EXACT document shape of css-pseudo/active-selection-051..057: bare head
// scaffolding after the doctype (no <html>, no <head> wrapper), then a <body>
// start tag that NEVER closes. Step 1 needs a </body>; step 2 anchors at ^ and
// finds <meta>, not html/head/body — so before step 1.5 the buffer stayed the
// WHOLE document and the bare <body …> start tag became a phantom component.
const UNCLOSED_BODY_DOC = '<!DOCTYPE html>\n\n  <meta charset="UTF-8">\n'
  + '  <title>t</title>\n  <link rel="match" href="x-ref.html">\n'
  + '  <style>div { color: transparent }</style>\n'
  + '  <script>function startTest(){}</script>\n'
  + '  <body onload="startTest();">\n\n'
  + '  <p>Test passes if "Selected Text" appears selected.\n\n'
  + '  <div id="test">Selected Text</div>\n';

test('S-RC1: locateBodyContent slices at an UNCLOSED <body> that is not the first tag', () => {
  const { inner, fallback } = locateBodyContent(UNCLOSED_BODY_DOC);
  assert.equal(fallback, true);                       // no <body>…</body> PAIR
  assert.ok(inner.includes('<p>Test passes'), `lost body content: ${inner}`);
  assert.ok(inner.includes('<div id="test">'));
  // The head scaffolding — and crucially the <body> START TAG itself — must
  // be gone: its survival is what manufactured the phantom component.
  assert.equal(/<body\b/i.test(inner), false, 'body start tag survived into the buffer');
  assert.equal(inner.includes('<title>'), false);
  assert.equal(inner.includes('<script>'), false);
});

test('S-RC1: the unclosed-body shape emits NO phantom `body` component', () => {
  // End-to-end: two real components (the instruction <p> and the styled div)
  // and nothing else. Pre-fix this produced THREE, the first a 100x100
  // placeholder built from the bare <body …> start tag.
  const { components } = buildComponents(
    UNCLOSED_BODY_DOC, parseCss('div { color: transparent }'), 'as');
  assert.deepEqual(Object.keys(components), ['as__0', 'as__1']);
  assert.equal(components.as__0._tag, 'p');
  assert.equal(components.as__1.properties.color, 'transparent');
});

test('S-RC1: a well-formed <body>…</body> pair still short-circuits at step 1', () => {
  // Step 1.5 must never run when step 1 already answered — fallback stays
  // false so the callers keep skipping the HEAD_ONLY filter.
  const html = '<html><head><title>x</title></head><body><div id=a></div></body></html>';
  const { inner, fallback } = locateBodyContent(html);
  assert.equal(fallback, false);
  assert.equal(inner, '<div id=a></div>');
});

test('S-RC1 guard 1: a <body> inside a SCRIPT string cannot be sliced at', () => {
  // cssom/computed-style-002's shape: `frmDoc.write('<body …>…')`. The masked
  // scan blanks raw-text element content, so the real document (which has no
  // body tag at all) falls through to the step-2 peel unchanged.
  const html = '<!DOCTYPE html>\n<div id="real">x</div>\n'
    + '<script>frmDoc.write(\'<body style="margin:0"><div style="width:100%"></div>\');</script>';
  const { inner } = locateBodyContent(html);
  assert.ok(inner.includes('<div id="real">x</div>'), 'real content dropped');
});

test('S-RC1 guard 1: a <body> inside a COMMENT cannot be sliced at', () => {
  // css-flexbox/flexbox-root-node-001b's shape: prose about "no explicit
  // <body>" in a comment ahead of the real markup.
  const html = '<!DOCTYPE html>\n<!-- checks display:flex on the root with no explicit <body>. -->\n'
    + '<html style="display: flex"><head><title>t</title></head>\n<div id="real">x</div>\n</html>';
  const { inner } = locateBodyContent(html);
  assert.ok(inner.includes('<div id="real">x</div>'));
});

test('S-RC1 guard 1: a <body> inside an ATTRIBUTE VALUE cannot be sliced at', () => {
  // css-writing-modes/orthogonal-root-resize-icb-001's shape: the body tag
  // lives in an iframe `src="data:text/html,…<body …>…"` AFTER the real
  // content, so slicing there would have thrown the <p> and the <iframe> away.
  const html = '<!DOCTYPE html>\n<html>\n  <link rel="help" href="x">\n'
    + '  <p>Test passes if there is a filled green square.</p>\n'
    + '  <iframe id="f" src="data:text/html,<!DOCTYPE html><body style=\'margin:0\'>'
    + '<div style=\'width:10px\'></div>"></iframe>\n</html>';
  const { inner } = locateBodyContent(html);
  assert.ok(inner.includes('<p>Test passes'), 'lost the instruction paragraph');
  assert.ok(inner.includes('<iframe id="f"'), 'lost the iframe');
});

test('S-RC1 guard 2: a <body> AFTER real content is the ignorable parse error', () => {
  // css-contain/content-visibility/slot-content-visibility-3-crash's shape.
  // Per HTML §13.2.6.4.7 a <body> start tag seen while already IN BODY is a
  // parse error the parser ignores (it only merges attributes), so the
  // implicit body — which already holds the <div> — must not be truncated.
  const html = '<!DOCTYPE html>\n<link rel=author href="mailto:x">\n'
    + '<div><span>content</span></div>\n<body hidden>\n<span id="late"></span>\n';
  const { inner } = locateBodyContent(html);
  assert.ok(inner.includes('<div><span>content</span></div>'),
    'truncated at an ignorable <body> parse error');
});

test('S-RC1: maskNonMarkupForBodyScan is LENGTH-PRESERVING (indices stay valid)', () => {
  // The whole slice-the-original trick depends on this: a shorter mask would
  // make every match index point at the wrong byte of the real document.
  const html = '<!-- c --><style>a{}</style><script>1<2</script><p title="<body>">x</p>';
  const masked = maskNonMarkupForBodyScan(html);
  assert.equal(masked.length, html.length);
  // …and every non-markup region really is blanked.
  assert.equal(/<body/i.test(masked), false);
  assert.equal(masked.includes('1<2'), false);
  // The surviving markup is still findable at its ORIGINAL offset.
  assert.equal(masked.indexOf('<p '), html.indexOf('<p '));
});

test('S-RC1: isBeforeBodyPrefix accepts head scaffolding and rejects flow content', () => {
  assert.equal(isBeforeBodyPrefix('<!DOCTYPE html>\n  <meta charset="UTF-8">\n  '), true);
  assert.equal(isBeforeBodyPrefix('<html><head></head>'), true);
  assert.equal(isBeforeBodyPrefix('   \n\t '), true);          // whitespace only
  assert.equal(isBeforeBodyPrefix('<div></div>'), false);      // flow element
  assert.equal(isBeforeBodyPrefix('<p>prose'), false);
  // Non-whitespace TEXT also opens the body (§13.2.6.4.4 character token).
  assert.equal(isBeforeBodyPrefix('<meta charset="UTF-8">stray text'), false);
});

// ── S-RC2: the &NewLine; / &Tab; named references ───────────────────────────

test('S-RC2: decodeCharacterReferences decodes &NewLine; and &Tab;', () => {
  // active-selection-057's subtest3 is literally `&NewLine;&NewLine;`.
  assert.equal(decodeCharacterReferences('&NewLine;&NewLine;'), '\n\n');
  assert.equal(decodeCharacterReferences('a&Tab;b'), 'a\tb');
  // Both spellings of TAB must now agree (the numeric one shipped in wave-15).
  assert.equal(decodeCharacterReferences('&Tab;'), decodeCharacterReferences('&#9;'));
});

test('S-RC2: the named-reference lookup stays CASE-SENSITIVE', () => {
  // HTML's table spells these with capitals; `&newline;` and `&tab;` are NOT
  // defined names, so the conservative "unknown names stay verbatim" contract
  // must keep them visible rather than silently decoding them.
  assert.equal(decodeCharacterReferences('&newline;'), '&newline;');
  assert.equal(decodeCharacterReferences('&tab;'), '&tab;');
  assert.equal(decodeCharacterReferences('&NEWLINE;'), '&NEWLINE;');
});

test('S-RC2: &amp;NewLine; still decodes to the LITERAL string (no rescan)', () => {
  // The single left-to-right pass never rescans its own output — the same
  // answer a real HTML tokenizer gives.
  assert.equal(decodeCharacterReferences('&amp;NewLine;'), '&NewLine;');
});

test('S-RC2: a pre-family element keeps the decoded newlines verbatim', () => {
  // The end-to-end shape of active-selection-057 subtest3: `white-space: pre`
  // routes the text through the preserve path, so the two decoded U+000A
  // survive into `_text` instead of collapsing to a single space. Undecoded,
  // this component painted the literal 18-char '&NewLine;&NewLine;' at
  // font-size:100px — the opposite of the test's "nothing viewable" assert.
  const html = '<style>div#s3 { white-space: pre; font-size: 100px }</style>'
    + '<div id="s3">&NewLine;&NewLine;</div>';
  const { components } = buildComponents(html, parseCss('div#s3 { white-space: pre; font-size: 100px }'), 'nl');
  assert.equal(components.nl__0._text, '\n\n');
});

// ═════════════════════════════════════════════════════════════════════════════
// wave-30 lane A: the extractor's selector engine
//   A1 :dir()  ·  A2 sibling combinators + the styledTags fallback
//   A3 the unsupported-rule count  ·  A4 root-inheritance BAKE-DOWN
//   A7 UA hyperlink styling
// ═════════════════════════════════════════════════════════════════════════════

// New exports under test — dynamic import, same pattern as the blocks above.
const {
  resolveDirectionality,
  countUnsupportedRules,
  bodyDeclaresInheritedProperty,
  shouldSlotBodyChildren,
  rootInheritedBakeProps,
  uaLinkProps,
  parseFontShorthand,
  rootPropsWithFontShorthand,
} = await import('./extract-fixture.mjs');

// ── A1: :dir() (Selectors-4 §11.2) ──────────────────────────────────────────

test('wave30 A1: :dir(ltr) matches an element whose own dir attribute is ltr', () => {
  // dir-selector-ltr-001's whole assertion, at the matcher level.
  assert.equal(selectorMatches('div:dir(ltr)', 'div', { dir: 'ltr' }), true);
  assert.equal(selectorMatches('div:dir(rtl)', 'div', { dir: 'ltr' }), false);
  assert.equal(selectorMatches('div:dir(rtl)', 'div', { dir: 'RTL' }), true); // ASCII-ci
});

test('wave30 A1: an INVALID :dir() argument drops the whole rule', () => {
  // CSS 2.2 §4.1.7 — an invalid selector invalidates its rule. This is what
  // makes dir-selector-ltr-002 / -003 pass: the red rule must never apply.
  for (const sel of ['div:dir(ltrr)', 'div:dir(ltr, rtl)', 'div:dir()',
    'div:dir(auto)', 'div:dir(LTR extra)']) {
    assert.equal(selectorMatches(sel, 'div', { dir: 'ltr' }), false, sel);
  }
});

test('wave30 A1: ltr-002 / ltr-003 keep painting their green base', () => {
  // End-to-end shape of both tests: the base rule paints green, the invalid
  // :dir() rule would paint red, and the fixture must show green.
  const html = '<body><div dir="ltr"></div></body>';
  for (const bad of ['div:dir(ltrr)', 'div:dir(ltr, rtl)']) {
    const css = `div { width:100px; height:100px; background-color:green } ${bad} { background-color:red }`;
    const { components } = buildComponents(html, parseCss(css), 't');
    assert.equal(components.t__0.properties['background-color'], 'green', bad);
  }
});

test('wave30 A1: ltr-001 now paints green (the rule that was dropped)', () => {
  const css = 'div { width:100px; height:100px; background-color:red }'
            + ' div:dir(ltr) { background-color:green }';
  const { components } = buildComponents(
    '<body><div dir="ltr"></div></body>', parseCss(css), 't');
  assert.equal(components.t__0.properties['background-color'], 'green');
});

test('wave30 A1: resolveDirectionality walks own attr → ancestors → ltr', () => {
  // Rung 1 — own attribute.
  assert.equal(resolveDirectionality({ dir: 'rtl' }), 'rtl');
  // Rung 1, dir=auto — first-strong over the stamped subtree text.
  assert.equal(resolveDirectionality({ dir: 'auto' }, { subtreeText: 'فارسی' }), 'rtl');
  assert.equal(resolveDirectionality({ dir: 'auto' }, { subtreeText: 'français' }), 'ltr');
  // Rung 2 — nearest ancestor wins over a farther one.
  assert.equal(resolveDirectionality({}, null, [
    { tag: 'div', attrs: { dir: 'rtl' } },
    { tag: 'div', attrs: { dir: 'ltr' } },
  ]), 'ltr');
  assert.equal(resolveDirectionality({}, null, [
    { tag: 'div', attrs: { dir: 'rtl' } },
    { tag: 'div', attrs: {} },
  ]), 'rtl');
  // An INVALID dir value leaves "no directionality state" (HTML §15.3.4) —
  // the element inherits as if the attribute were absent.
  assert.equal(resolveDirectionality({ dir: 'ltrl' }, null,
    [{ tag: 'div', attrs: { dir: 'rtl' } }]), 'rtl');
  // Rung 3 — the document default.
  assert.equal(resolveDirectionality({}), 'ltr');
  assert.equal(resolveDirectionality({}, null, []), 'ltr');
});

test('wave30 A1: :dir() reads the ATTRIBUTE, never the CSS direction property', () => {
  // Selectors-4 §11.2. dir-selector-change-001 sets `direction: ltr` in CSS
  // and `dir=rtl` from script; the attribute decides. Here the ancestor
  // carries only the CSS property in its resolved bag — which the matcher
  // never sees — so the element stays at the ltr default.
  assert.equal(selectorMatches('div:dir(rtl)', 'div', { style: 'direction:rtl' }), false);
  assert.equal(selectorMatches('div:dir(ltr)', 'div', { style: 'direction:rtl' }), true);
});

test('wave30 A1: :dir() inherits through the ancestor chain at match time', () => {
  const ancestors = [{ tag: 'div', attrs: { dir: 'rtl' } }, { tag: 'div', attrs: {} }];
  assert.equal(selectorMatches('span:dir(rtl)', 'span', {}, ancestors), true);
  assert.equal(selectorMatches('span:dir(ltr)', 'span', {}, ancestors), false);
});

// ── A2: sibling combinators (Selectors-4 §15.4 / §15.5) ─────────────────────

test('wave30 A2: splitSelectorChain tokenises + and ~', () => {
  assert.deepEqual(splitSelectorChain('div + p'),
    { compounds: ['div', 'p'], combinators: ['+'] });
  assert.deepEqual(splitSelectorChain('div~p'),
    { compounds: ['div', 'p'], combinators: ['~'] });
  assert.deepEqual(splitSelectorChain('.a > .b + .c .d'),
    { compounds: ['.a', '.b', '.c', '.d'], combinators: ['>', '+', ' '] });
  // The `+` of an An+B argument is NOT a combinator (paren depth).
  assert.deepEqual(splitSelectorChain('p:nth-child(2n + 1)'),
    { compounds: ['p:nth-child(2n + 1)'], combinators: [] });
});

// A one-parent sibling scope: three element children, the subject is index 2.
const SIBS = [
  { tag: 'div', attrs: { id: 'x' } },
  { tag: 'em', attrs: {} },
  { tag: 'span', attrs: {} },
];
const SUBJECT_POS = { isRoot: false, sibIndex: 2, sibCount: 3, siblings: SIBS };
const ONE_PARENT = [{ tag: 'body', attrs: {}, pos: { isRoot: true } }];

test('wave30 A2: + matches ONLY the immediately preceding sibling', () => {
  assert.equal(selectorMatches('em + span', 'span', {}, ONE_PARENT, SUBJECT_POS), true);
  // #x is two slots back — adjacency must refuse it.
  assert.equal(selectorMatches('#x + span', 'span', {}, ONE_PARENT, SUBJECT_POS), false);
});

test('wave30 A2: ~ matches ANY preceding sibling', () => {
  assert.equal(selectorMatches('#x ~ span', 'span', {}, ONE_PARENT, SUBJECT_POS), true);
  assert.equal(selectorMatches('em ~ span', 'span', {}, ONE_PARENT, SUBJECT_POS), true);
  // A tag that is not among the preceding siblings still misses.
  assert.equal(selectorMatches('code ~ span', 'span', {}, ONE_PARENT, SUBJECT_POS), false);
});

test('wave30 A2: a sibling rule never matches without sibling metadata', () => {
  // No `pos` at all ⇒ adjacency is unprovable ⇒ honest miss, NOT a degrade
  // to a descendant match (which would style boxes the browser does not).
  assert.equal(selectorMatches('em + span', 'span', {}, ONE_PARENT), false);
  // …and with no ancestor chain either, the structural-chain bail applies.
  assert.equal(selectorMatches('em + span', 'span', {}), false);
});

test('wave30 A2: sibling steps do not consume the ancestor budget', () => {
  // `body em + span`: the descendant step still has <body> available after
  // the sibling step, because siblings share one parent.
  assert.equal(selectorMatches('body em + span', 'span', {}, ONE_PARENT, SUBJECT_POS), true);
  assert.equal(selectorMatches('main em + span', 'span', {}, ONE_PARENT, SUBJECT_POS), false);
});

test('wave30 A2: a sibling compound resolves :dir() from the shared chain', () => {
  // The mechanism selectors__dir-selector-auto-direction-change-001 needs:
  // `:dir(ltr) + #target` asks about the PREVIOUS SIBLING's directionality.
  const sibs = [
    { tag: 'div', attrs: { dir: 'rtl' } },
    { tag: 'div', attrs: { id: 'target' } },
  ];
  const pos = { isRoot: false, sibIndex: 1, sibCount: 2, siblings: sibs };
  assert.equal(selectorMatches(':dir(rtl) + #target', 'div', { id: 'target' },
    ONE_PARENT, pos), true);
  assert.equal(selectorMatches(':dir(ltr) + #target', 'div', { id: 'target' },
    ONE_PARENT, pos), false);
});

test('wave30 A2: a dir=auto sibling resolves from its stamped subtree text', () => {
  // The walker stamps `subtreeText` onto each entry of the shared sibling
  // list precisely so this works (see extractBodyTreeNested's second pass).
  const mk = (text) => [
    { tag: 'div', attrs: { dir: 'auto' }, subtreeText: text },
    { tag: 'div', attrs: { id: 'target' } },
  ];
  for (const [text, expect] of [['رسمية', false], ['LTR', true]]) {
    const sibs = mk(text);
    const pos = { isRoot: false, sibIndex: 1, sibCount: 2, siblings: sibs };
    assert.equal(
      selectorMatches(':dir(ltr) + #target', 'div', { id: 'target' }, ONE_PARENT, pos),
      expect, text);
  }
});

test('wave30 A2: extractBodyTreeNested stamps subtreeText on pos AND siblings', () => {
  const tree = extractBodyTreeNested(
    '<body><div dir="auto"><span>رسمية</span></div><div id="t"></div></body>', 5);
  assert.equal(tree[0].pos.subtreeText, 'رسمية');
  // Shared sibling list carries the same scalars (never a pos back-reference,
  // which would make the tree cyclic).
  assert.equal(tree[1].pos.siblings[0].subtreeText, 'رسمية');
  assert.equal(tree[1].pos.siblings[1].isEmpty, true);
  assert.doesNotThrow(() => JSON.stringify(tree));
});

test('wave30 A2b: collectStyledTags raw-scans a MALFORMED chain', () => {
  // `> span` cannot be tokenised, so no host compound is identifiable; the
  // guard over-protects rather than letting <span> be merged away.
  assert.ok(collectStyledTags([{ selector: '> span', props: { color: 'red' } }]).has('span'));
  // Class / id / pseudo tokens must NOT leak in as tag names.
  const tags = collectStyledTags([{ selector: '.note >> #x:first-child', props: { color: 'red' } }]);
  assert.deepEqual([...tags].sort(), []);
});

test('wave30 A2: dir-selector-change-001 keeps its <span> as a component', () => {
  // The measured regression: with `#x:dir(rtl) + span` dropped, styledTags
  // was empty, the span was inline-merged into its parent's text, and the
  // lime box vanished from the fixture entirely.
  const css = '#x:dir(rtl) + span { background-color: lime } #outer { direction:ltr }';
  const html = '<body><div id="outer"><div><div id="x"></div>'
             + '<span>The background color should be lime</span></div></div></body>';
  const { components } = buildComponents(html, parseCss(css), 't');
  const inner = components.t__0.children.t__0__0;
  const span = inner.children.t__0__0__1;
  assert.equal(span._text, 'The background color should be lime');
  // Statically the dir attribute is absent (the script adds it), so the lime
  // is NOT baked here — post-load's computed overlay delivers it. What this
  // pin guards is that the component EXISTS to receive it.
  assert.equal(span.properties['background-color'], undefined);
});

test('wave30 A2: a static + rule that DOES match bakes its declaration', () => {
  const css = '#x:dir(ltr) + span { background-color: lime }';
  const html = '<body><div><div id="x"></div><span>t</span></div></body>';
  const { components } = buildComponents(html, parseCss(css), 't');
  assert.equal(components.t__0.children.t__0__1.properties['background-color'], 'lime');
});

// ── A3: the unsupported-rule count ──────────────────────────────────────────

test('wave30 A3: countUnsupportedRules counts what the matcher drops', () => {
  const rules = parseCss(
    'div { color: red }' +               // matchable
    'p[hidden] { color: red }' +         // attribute selector → dropped
    'a:hover { color: red }' +           // unmodelled pseudo → dropped
    'div:dir(ltrr) { color: red }' +     // invalid :dir() argument → dropped
    '.a + .b { color: red }' +           // sibling combinator → NOW matchable
    'p::first-line { color: red }'       // unmodelled pseudo-element → dropped
  );
  assert.equal(countUnsupportedRules(rules), 4);
  assert.equal(countUnsupportedRules([]), 0);
  assert.equal(countUnsupportedRules(undefined), 0);
});

test('wave30 A3: an unsupported NON-rightmost compound still counts', () => {
  // selectorMatchesPseudoElement pre-flights every compound, so a rule whose
  // ancestor compound is unsupported matches nothing either.
  assert.equal(countUnsupportedRules(parseCss('[data-x] span { color: red }')), 1);
});

// ── A4: root-inheritance BAKE-DOWN ──────────────────────────────────────────
//
// The gate finding that produced this shape: delivering root inheritance by
// SLOTTING (nesting the body children under an UNSIZED body-root) held on web
// but cost both natives ~0.16 SSIM on text-decoration-inset-001/002, and
// restructured 342 corpus fixtures while doing it. The trigger set is
// unchanged; only the delivery is. These pins are the contract:
//   1. the trigger set is exactly the closed list (unchanged from wave-30);
//   2. slotting fires on the HEIGHT arm ONLY;
//   3. the baked values are the root's, on the top-level children;
//   4. the child's own declaration wins (css-cascade-4 §7.3);
//   5. …including via a shorthand that COVERS the longhand;
//   6. the copy is LOUD.

test('wave30 A4: bodyDeclaresInheritedProperty fires on inherited props only', () => {
  assert.equal(bodyDeclaresInheritedProperty({ color: 'red' }), true);
  assert.equal(bodyDeclaresInheritedProperty({ 'font-size': '50px' }), true);
  assert.equal(bodyDeclaresInheritedProperty({ direction: 'rtl' }), true);
  assert.equal(bodyDeclaresInheritedProperty({ 'caret-color': 'orange' }), true);
  // Non-inherited declarations change nothing for the children.
  assert.equal(bodyDeclaresInheritedProperty({ 'background-color': 'red' }), false);
  assert.equal(bodyDeclaresInheritedProperty({ margin: '0', contain: 'layout' }), false);
  assert.equal(bodyDeclaresInheritedProperty({}), false);
  assert.equal(bodyDeclaresInheritedProperty(null), false);
});

test('wave30 A4: the trigger set is the pinned closed list', () => {
  // Pinned member-by-member: a silent widening changes what every top-level
  // component in the corpus carries, and must never land unreviewed.
  for (const p of ['font-size', 'font-family', 'font-weight', 'font-style',
    'color', 'line-height', 'direction', 'caret-color', 'letter-spacing',
    'word-spacing', 'text-align', 'visibility',
    // wave-36 lane M3: `quotes` (css-content-3 §2.2) joins the list — the
    // reviewed widening this pin exists to force. Motivating cells:
    // css-content quotes-031 (`body { quotes: "‹" "›" }`, 0.8701) and
    // quotes-032 (`body { quotes: none }`, 0.8822), both painting the
    // initial `auto` marks because the root declaration reached nothing.
    'quotes']) {
    assert.equal(bodyDeclaresInheritedProperty({ [p]: 'x' }), true, p);
  }
  // Inherited but deliberately OUTSIDE the list (the documented gap) — and
  // non-inherited properties, which could never matter.
  for (const p of ['white-space', 'text-indent', 'cursor',
    'background-color', 'padding', 'height', 'contain']) {
    assert.equal(bodyDeclaresInheritedProperty({ [p]: 'x' }), false, p);
  }
});

test('wave30 A4: shouldSlotBodyChildren fires on the HEIGHT arm ONLY', () => {
  // The gate finding: an inherited-only root must NOT restructure the
  // document, because the resulting unsized-parent shape mis-renders on both
  // natives (inset-001 android 0.9742→0.8162 · ios 0.9576→0.8160).
  assert.equal(shouldSlotBodyChildren({ height: '4000px' }), true);   // wave-17
  assert.equal(shouldSlotBodyChildren({ color: 'red' }), false);      // baked
  assert.equal(shouldSlotBodyChildren({ 'font-size': '50px' }), false);
  assert.equal(shouldSlotBodyChildren({ height: '0px' }), false);
  assert.equal(shouldSlotBodyChildren({ 'background-color': 'red' }), false);
  // Both together: the height arm still wins and the document nests.
  assert.equal(shouldSlotBodyChildren({ height: '4000px', color: 'red' }), true);
});

test('wave30 A4: rootInheritedBakeProps copies only declared trigger props', () => {
  assert.deepEqual(
    rootInheritedBakeProps({ 'font-size': '50px', 'caret-color': 'orange' }, {}),
    { 'font-size': '50px', 'caret-color': 'orange' });
  // Non-trigger keys on the root are never handed down, inherited or not.
  assert.equal(
    rootInheritedBakeProps({ 'background-color': 'red', 'white-space': 'pre' }, {}),
    null);
  // Nothing to give / nobody to give it to.
  assert.equal(rootInheritedBakeProps({}, {}), null);
  assert.equal(rootInheritedBakeProps(null, {}), null);
  assert.equal(rootInheritedBakeProps({ color: 'red' }, null), null);
});

test('wave30 A4: the child\'s OWN declaration beats the root (cascade §7.3)', () => {
  // The child declares font-size itself → it keeps 9px and only `color`
  // is handed down.
  assert.deepEqual(
    rootInheritedBakeProps({ 'font-size': '50px', color: 'red' },
      { 'font-size': '9px' }),
    { color: 'red' });
  // Every trigger prop declared → nothing left to bake.
  assert.equal(
    rootInheritedBakeProps({ color: 'red' }, { color: 'green' }), null);
});

test('wave30 A4: a shorthand that COVERS the longhand suppresses the bake', () => {
  // css-fonts-4 §6: `font` sets size/family/weight/style and resets
  // line-height, so none of the five may be baked over it — even though
  // their names never appear in the child's bag.
  assert.equal(
    rootInheritedBakeProps(
      { 'font-size': '50px', 'font-family': 'serif', 'font-weight': '700',
        'font-style': 'italic', 'line-height': '2' },
      { font: '12px serif' }),
    null);
  // …but `font` says nothing about colour, which still comes down.
  assert.deepEqual(
    rootInheritedBakeProps({ 'font-size': '50px', color: 'red' },
      { font: '12px serif' }),
    { color: 'red' });
  // css-ui-4 §7.2: `caret` covers caret-color.
  assert.equal(
    rootInheritedBakeProps({ 'caret-color': 'orange' }, { caret: 'auto' }), null);
  // css-cascade-4 §3.2: `all` covers every longhand EXCEPT direction.
  assert.deepEqual(
    rootInheritedBakeProps(
      { 'font-size': '50px', color: 'red', direction: 'rtl' }, { all: 'unset' }),
    { direction: 'rtl' });
});

test('wave30 A4: an inherited root declaration BAKES onto the body children', () => {
  // caret-color-visited-inheritance's shape: the 50px must reach the text.
  const { components, lossyReasons } = buildComponents(
    '<body><main><a href="">link</a></main></body>',
    parseCss(':root { font-size: 50px; caret-color: orange }'), 't');
  assert.equal(components.t__body._role, 'body-root');
  // SHAPE: the <main> stays a top-level SIBLING — no nesting, no unsized
  // slotted parent for the natives to mis-size.
  assert.equal(components.t__body.children, undefined);
  assert.equal(components.t__0._tag, 'main');
  // VALUES: the root's declarations now ride on the child's own bag.
  assert.equal(components.t__0.properties['font-size'], '50px');
  assert.equal(components.t__0.properties['caret-color'], 'orange');
  // …and the root keeps its own bag untouched (the composed canvases read it).
  assert.equal(components.t__body.properties['font-size'], '50px');
  // LOUD, once, and rolled up to the document.
  assert.deepEqual(components.t__0._lossyReasons, ['body-inherited-baked']);
  assert.equal(components.t__0._lossy, true);
  assert.ok(lossyReasons.includes('body-inherited-baked'));
  // The DEEPER <a> is untouched: it inherits from <main> through each
  // runtime's own INHERITED_PROPERTY_TYPES merge — only the top-level hop
  // was ever missing.
  const a = components.t__0.children.t__0__0;
  assert.equal(a.properties['font-size'], undefined);
});

test('wave30 A4: the bake reaches body-level TEXT runs too', () => {
  // A bare body text run is a body child like any element — prose rendered
  // at the root's font-size, not the 16px default.
  const { components } = buildComponents(
    '<body>lead<div>x</div></body>', parseCss('body { color: red }'), 't');
  assert.equal(components.t__text.properties.color, 'red');
  assert.ok(components.t__text._lossyReasons.includes('body-inherited-baked'));
});

// ── wave-35 lane FX: the font-SHORTHAND bake ────────────────────────────────
//
// The measured shape: css/css-text/boundary-shaping/*.html declare their whole
// typography as `body { font: 36px test }` and nothing else, so every longhand
// trigger was absent and the bake never fired — the three text components of
// `of<span class=a>f</span>ice` shipped empty bags and painted 16px Inter
// against a browser-ref painting 36px LinLibertine.

test('wave35 FX: parseFontShorthand derives the §3.7 size/family form', () => {
  // The corpus shape, and the minimum grammar: size + family.
  assert.deepEqual(parseFontShorthand('36px test'),
    { 'font-size': '36px', 'line-height': 'normal', 'font-family': 'test' });
  assert.deepEqual(parseFontShorthand('16px serif'),
    { 'font-size': '16px', 'line-height': 'normal', 'font-family': 'serif' });
  // The full pre-size run + an explicit line-height + a family LIST.
  assert.deepEqual(parseFontShorthand('italic bold 16px/1.2 X, serif'), {
    'font-style': 'italic', 'font-weight': 'bold', 'font-size': '16px',
    'line-height': '1.2', 'font-family': 'X, serif',
  });
  // css-fonts-4 §3.4 `oblique <angle>` is ONE font-style value.
  assert.deepEqual(parseFontShorthand('oblique 20deg 16px serif'), {
    'font-style': 'oblique 20deg', 'font-size': '16px',
    'line-height': 'normal', 'font-family': 'serif',
  });
  // Numeric weight, a variant + stretch keyword that are parsed then dropped
  // (neither is a trigger property), and a QUOTED family name surviving whole.
  assert.deepEqual(
    parseFontShorthand('small-caps 700 condensed 1.2em "Lucida Grande", sans-serif'), {
      'font-weight': '700', 'font-size': '1.2em', 'line-height': 'normal',
      'font-family': '"Lucida Grande", sans-serif',
    });
  // `normal` is legal in all four pre-size slots and names none of them —
  // consumed, never emitted (it is every slot's initial value anyway).
  assert.deepEqual(parseFontShorthand('normal normal normal 16px/normal Arial'), {
    'font-size': '16px', 'line-height': 'normal', 'font-family': 'Arial',
  });
});

test('wave35 FX: the `/` delimiter binds through any whitespace (syntax-3 §5)', () => {
  // All four spellings are the SAME declaration — the bug FontExpander.kt's
  // joinSlashRuns exists for, ported so the two readings cannot diverge.
  const want = { 'font-size': '12px', 'line-height': '30px', 'font-family': 'Georgia' };
  for (const v of ['12px/30px Georgia', '12px/ 30px Georgia',
    '12px /30px Georgia', '12px / 30px Georgia']) {
    assert.deepEqual(parseFontShorthand(v), want, v);
  }
});

test('wave35 FX: the REFUSALS are honest — no half-derived font', () => {
  // <system-family-name> (css-fonts-4 §3.7): resolved from the PLATFORM font
  // database, so there is no size and no family in the document to derive.
  for (const kw of ['caption', 'icon', 'menu', 'message-box', 'small-caption',
    'status-bar', 'MENU']) {
    assert.equal(parseFontShorthand(kw), null, kw);
  }
  // The CSS-wide keywords set every longhand to "ask the cascade again", and
  // the fixture wire has no cascade to ask.
  for (const kw of ['inherit', 'initial', 'unset', 'revert', 'revert-layer']) {
    assert.equal(parseFontShorthand(kw), null, kw);
  }
  // var() — unresolvable by construction; after it, even WHICH token is the
  // size is unknowable.
  assert.equal(parseFontShorthand('var(--x) serif'), null);
  assert.equal(parseFontShorthand('16px var(--f)'), null);
  // Structurally invalid: missing family, missing size, an unrecognised token
  // before the size, an out-of-range numeric weight, a dangling slash.
  for (const v of ['16px', 'serif', 'wibble 16px serif', '1200 16px serif',
    '16px/ Georgia', 'bold 100 16px serif', '', '   ']) {
    assert.equal(parseFontShorthand(v), null, JSON.stringify(v));
  }
  assert.equal(parseFontShorthand(undefined), null);
  assert.equal(parseFontShorthand(null), null);
});

test('wave35 FX: derived longhands agree with the converter\'s FontExpander', () => {
  // PARITY PIN. The body-root keeps its `font` shorthand and :converter
  // expands it with FontExpander.kt; the children get THIS function's
  // derivation. Any disagreement would put one document's root and its own
  // children on two different readings of the same declaration. The three
  // decisions that could drift are pinned here:
  //   1. §4.3 — a shorthand with no `/<line-height>` RESETS it to `normal`
  //      (FontExpander.kt's closing `result["line-height"] = "normal"`).
  assert.equal(parseFontShorthand('36px test')['line-height'], 'normal');
  //   2. an explicit `/<line-height>` wins over that reset.
  assert.equal(parseFontShorthand('36px/2 test')['line-height'], '2');
  //   3. the system-font path emits NO size and NO line-height on either side.
  assert.equal(parseFontShorthand('menu'), null);
});

test('wave35 FX: a root `font` shorthand fires the trigger and BAKES', () => {
  assert.equal(bodyDeclaresInheritedProperty({ font: '36px test' }), true);
  // …but a refused form does not — the bake stays exactly as it was.
  assert.equal(bodyDeclaresInheritedProperty({ font: 'menu' }), false);
  assert.equal(bodyDeclaresInheritedProperty({ font: 'inherit' }), false);
  assert.deepEqual(rootInheritedBakeProps({ font: '36px test' }, {}),
    { 'font-size': '36px', 'font-family': 'test', 'line-height': 'normal' });
});

test('wave35 FX: every existing guard still beats a DERIVED longhand', () => {
  // Guard 2 (INHERITED_COVERING_SHORTHANDS): the child's own `font` covers
  // all five, so a root `font` hands down nothing at all.
  assert.equal(
    rootInheritedBakeProps({ font: '36px test' }, { font: '12px serif' }), null);
  // Guard 1: the child's own longhand wins per property — it keeps 9px and
  // only the keys it never spoke about arrive.
  assert.deepEqual(
    rootInheritedBakeProps({ font: '36px test' }, { 'font-size': '9px' }),
    { 'font-family': 'test', 'line-height': 'normal' });
  // `all` covers every derived longhand (none of the five is `direction`).
  assert.equal(
    rootInheritedBakeProps({ font: '36px test' }, { all: 'unset' }), null);
});

test('wave35 FX: the author\'s explicit longhand beats the derived one', () => {
  // Declaration order is not recoverable from the flattened root bag, so the
  // explicit longhand wins — the only merge order that cannot change a byte
  // any pre-FX fixture already baked.
  const merged = rootPropsWithFontShorthand(
    { font: '36px test', 'font-family': 'sans-serif' });
  assert.equal(merged['font-family'], 'sans-serif');
  assert.equal(merged['font-size'], '36px');       // still filled from the shorthand
  assert.equal(merged.font, '36px test');          // the shorthand itself is kept
  // No `font` key, or a refused one → the SAME OBJECT back, no allocation and
  // provably no behaviour change.
  const plain = { color: 'red' };
  assert.equal(rootPropsWithFontShorthand(plain), plain);
  const sys = { font: 'menu' };
  assert.equal(rootPropsWithFontShorthand(sys), sys);
});

test('wave35 FX: end-to-end — boundary-shaping\'s text gets the @font-face family', () => {
  // The exact corpus shape: `body { font: 36px test }` over
  // `of<span class=a>f</span>ice`, where `test` is the @font-face family
  // lane B2 delivers as fixture.fontFaces.
  const { components, lossyReasons } = buildComponents(
    '<body>of<span class=a>f</span>ice</body>',
    parseCss('body { font: 36px test } .a { vertical-align: initial }'), 't');
  for (const id of ['t__text', 't__0', 't__text1']) {
    assert.equal(components[id].properties['font-family'], 'test', id);
    assert.equal(components[id].properties['font-size'], '36px', id);
    assert.equal(components[id].properties['line-height'], 'normal', id);
    assert.ok(components[id]._lossyReasons.includes('body-inherited-baked'), id);
  }
  // The span keeps its OWN declaration alongside the baked ones.
  assert.equal(components.t__0.properties['vertical-align'], 'initial');
  // The root's own bag is untouched — it still carries the shorthand, which
  // the converter expands for the composed canvas.
  assert.equal(components.t__body.properties.font, '36px test');
  assert.equal(components.t__body.properties['font-family'], undefined);
  assert.ok(lossyReasons.includes('body-inherited-baked'));
});

test('wave30 A4: a NON-inherited-only root bag stays byte-for-byte legacy', () => {
  // No trigger property → no bake, no marker, no shape change.
  const { components } = buildComponents(
    '<body><div></div></body>', parseCss('body { background-color: red }'), 't');
  assert.equal(components.t__body.children, undefined);
  assert.deepEqual(components.t__0.properties, { width: '100px', height: '100px' });
  assert.equal(components.t__0._lossy, undefined);
});

test('wave30 A4: the bake runs AFTER the empty-node placeholder decision', () => {
  // The 100x100 placeholder means "this element declares nothing of its
  // own" — a handed-down value must not disguise that, or a scaffolding
  // wrapper would silently become a styled subject with no box.
  const { components } = buildComponents(
    '<body><div></div></body>', parseCss('body { color: red }'), 't');
  assert.equal(components.t__0.properties.width, '100px');
  assert.equal(components.t__0.properties.height, '100px');
  assert.equal(components.t__0.properties.color, 'red');
});

test('wave30 A4: a SLOTTED (sized) body is NOT also baked', () => {
  // Under the height arm the children are real descendants, so every
  // runtime's inherited merge already hands them the root's values through
  // the wire's parent edge — copying on top would turn an inherited value
  // into an own declaration.
  const { components } = buildComponents(
    '<body><div>x</div></body>',
    parseCss('body { height: 4000px; color: red }'), 't');
  const child = components.t__body.children.t__0;
  assert.equal(child.properties.color, undefined);
  assert.equal(child._lossyReasons, undefined);
});

test('wave30 A4: no emitted body-root means no bake', () => {
  // With no root-scope rule there is no inheritance SOURCE in the fixture,
  // so there is nothing to hand down — same gate slotting uses.
  const { components } = buildComponents(
    '<body><div>x</div></body>', parseCss('div { color: red }'), 't');
  assert.equal(components.t__body, undefined);
  assert.equal(components.t__0._lossyReasons, undefined);
});

// ── A7: UA hyperlink styling (HTML Rendering §15.5.2) ───────────────────────

test('wave30 A7: uaLinkProps fills the UA rule for an <a href>', () => {
  assert.deepEqual(uaLinkProps('a', { href: '' }, {}),
    { color: '#0000EE', 'text-decoration-line': 'underline' });
  // An <a> with NO href is not a hyperlink (HTML §4.6.1).
  assert.equal(uaLinkProps('a', { name: 'top' }, {}), null);
  assert.equal(uaLinkProps('span', { href: 'x' }, {}), null);
});

test('wave30 A7: author declarations beat the UA origin, per property', () => {
  // CSS Cascade 5 §6.1. The cascade is per-property, so declaring one does
  // not suppress the other.
  assert.deepEqual(uaLinkProps('a', { href: 'x' }, { color: 'red' }),
    { 'text-decoration-line': 'underline' });
  assert.deepEqual(uaLinkProps('a', { href: 'x' }, { 'text-decoration': 'none' }),
    { color: '#0000EE' });
  assert.deepEqual(uaLinkProps('a', { href: 'x' }, { 'text-decoration-line': 'overline' }),
    { color: '#0000EE' });
  // Both declared → nothing left to fill.
  assert.equal(uaLinkProps('a', { href: 'x' },
    { color: 'red', '-webkit-text-decoration': 'none' }), null);
});

test('wave30 A7: the bake rides a LOUD lossy marker', () => {
  const { components, lossyReasons } = buildComponents(
    '<body><a href="">link</a></body>', parseCss('body { color: green }'), 't');
  const a = components.t__0;   // top-level sibling (A4 bakes, never slots)
  // The UA rule is on the ELEMENT, so it beats the root's INHERITED green —
  // which is why A4's bake-down must not overwrite it (uaLinkProps ran
  // inside buildNode, so `color` was already in the bag when the bake's
  // author-wins guard looked).
  assert.equal(a.properties.color, '#0000EE');
  assert.equal(a.properties['text-decoration-line'], 'underline');
  assert.ok(a._lossyReasons.includes('ua-link-styling-baked'));
  assert.ok(lossyReasons.includes('ua-link-styling-baked'));
});

test('wave30 A7: an author colour on the link suppresses the UA colour', () => {
  const { components } = buildComponents(
    '<body><a href="">link</a></body>', parseCss('a { color: green }'), 't');
  assert.equal(components.t__0.properties.color, 'green');
});

// ═════════════════════════════════════════════════════════════════════════════
//   wave-30 fix-T1: A7's author-wins guard must also see the rules the
//   matcher DROPPED
// ═════════════════════════════════════════════════════════════════════════════

const { uaLinkSuppressedProps, selectorCouldTargetLink } =
  await import('./extract-fixture.mjs');

test('fix-T1: selectorCouldTargetLink sees link-state pseudos and bare `a` tags', () => {
  // Link-state pseudo-classes (Selectors-4 §11), wherever they sit.
  for (const sel of ['a:link', ':visited', 'p :any-link', '#x:local-link',
    ':target-current', 'main :is(a:visited > :where(.a + span))']) {
    assert.equal(selectorCouldTargetLink(sel), true, sel);
  }
  // A bare `a` TAG at the head of a compound — start, after a combinator,
  // after a comma, or inside a functional pseudo.
  for (const sel of ['a', 'div a', 'div > a', 'p, a', ':is(a)', 'a[href]']) {
    assert.equal(selectorCouldTargetLink(sel), true, sel);
  }
  // …and NOT a class/id/ident that merely starts with the letter a.
  for (const sel of ['.a', '#a', 'div.a', 'article', 'span.abc + .a',
    'div:hover', '[data-a]']) {
    assert.equal(selectorCouldTargetLink(sel), false, sel);
  }
});

test('fix-T1: a DROPPED link rule suppresses only the UA property it declares', () => {
  // `a:link` is an unsupported compound (`:link` is not in SUPPORTED_PSEUDOS),
  // so the matcher never applies the rule and the resolved bag the A7 guard
  // reads has no `color` in it at all.
  assert.equal(countUnsupportedRules(parseCss('a:link { color: red }')), 1);
  assert.deepEqual([...uaLinkSuppressedProps(parseCss('a:link { color: red }'))],
    ['color']);
  // Per-property: a dropped decoration rule leaves the UA colour alone.
  assert.deepEqual([...uaLinkSuppressedProps(
    parseCss('a:visited { text-decoration: none }'))], ['text-decoration-line']);
  // Both spellings, one rule.
  assert.deepEqual([...uaLinkSuppressedProps(
    parseCss('a:link { color: red; -webkit-text-decoration: none }'))].sort(),
    ['color', 'text-decoration-line']);
});

test('fix-T1: an unrelated dropped rule suppresses nothing', () => {
  // `div:hover` is dropped too (unmodelled pseudo) — but it neither targets
  // links nor declares a UA link property, so the bake must be untouched.
  assert.deepEqual([...uaLinkSuppressedProps(parseCss('div:hover { width: 10px }'))], []);
  // A link-targeting DROPPED rule that declares neither UA property.
  assert.deepEqual([...uaLinkSuppressedProps(parseCss('a:link { width: 10px }'))], []);
  // A link rule the matcher CAN apply is handled by the existing resolved-bag
  // guard, not by this one — no double-counting.
  assert.deepEqual([...uaLinkSuppressedProps(parseCss('a { color: green }'))], []);
});

test('fix-T1: css-color/color-mix-currentcolor-visited no longer gets a wrong blue', () => {
  // The test's whole sheet. `a:link { color: red }` is dropped, so pre-fix the
  // element resolved to an empty bag and A7 baked UA #0000EE over the author's
  // red — a GUARANTEED-wrong pixel. The underline still bakes (nothing
  // declares a decoration).
  const rules = parseCss('a:link { color: red } a:visited { color: green } '
    + 'span { background-color: color-mix(in srgb, currentcolor, white 75%) }');
  const { components } = buildComponents('<body><a href=""><span>x</span></a></body>', rules, 't');
  const a = components.t__0;
  assert.equal(a.properties.color, undefined, 'UA blue must not be baked');
  assert.equal(a.properties['text-decoration-line'], 'underline');
});

test('fix-T1: selectors/is-where-visited no longer gets a wrong blue', () => {
  // `:visited, :link { color: black }` — parseCss explodes the list into two
  // rules, both dropped, both declaring `color`.
  const rules = parseCss(':visited, :link { color: black } '
    + '#parent1 :is(:visited) { color: green }');
  assert.deepEqual([...uaLinkSuppressedProps(rules)], ['color']);
  const { components } = buildComponents('<body><a href="">a</a></body>', rules, 't');
  assert.equal(components.t__0.properties.color, undefined);
  assert.equal(components.t__0.properties['text-decoration-line'], 'underline');
});

test('fix-T1: a plain <a href> with no dropped link rules still bakes both', () => {
  const { components } = buildComponents('<body><a href="x">hi</a></body>',
    parseCss('div:hover { width: 10px }'), 't');
  assert.equal(components.t__0.properties.color, '#0000EE');
  assert.equal(components.t__0.properties['text-decoration-line'], 'underline');
  assert.ok(components.t__0._lossyReasons.includes('ua-link-styling-baked'));
  // …and with no rules at all (the pure A7 path).
  const bare = buildComponents('<body><a href="x">hi</a></body>', [], 't');
  assert.equal(bare.components.t__0.properties.color, '#0000EE');
});

test('fix-T1: the suppression is a per-property argument to uaLinkProps', () => {
  // Pinned at the unit level so a caller that forgets to thread the set
  // degrades to the OLD behaviour visibly rather than silently.
  assert.deepEqual(uaLinkProps('a', { href: '' }, {}, new Set(['color'])),
    { 'text-decoration-line': 'underline' });
  assert.equal(uaLinkProps('a', { href: '' }, {},
    new Set(['color', 'text-decoration-line'])), null);
  assert.deepEqual(uaLinkProps('a', { href: '' }, {}, new Set()),
    { color: '#0000EE', 'text-decoration-line': 'underline' });
  // Not a hyperlink → still null, suppression or not.
  assert.equal(uaLinkProps('a', {}, {}, new Set()), null);
});

// ── wave-33 lane N (N1): the load-bearing unclosed-wrapper adoption ──────────

test('N1: hasBoxAffectingInlineStyle recognises the offset/CB/extent set', () => {
  // Offsets — the wrapper's content edge is where its children start.
  assert.equal(hasBoxAffectingInlineStyle('margin-left: -100px'), true);
  assert.equal(hasBoxAffectingInlineStyle('padding:4px'), true);
  assert.equal(hasBoxAffectingInlineStyle('border-block-end-width: 2px'), true);
  // Containing-block establishment.
  assert.equal(hasBoxAffectingInlineStyle('position:relative;'), true);
  assert.equal(hasBoxAffectingInlineStyle('transform: translateX(4px)'), true);
  // Extent.
  assert.equal(hasBoxAffectingInlineStyle('display: inline-block; color: red'), true);
  assert.equal(hasBoxAffectingInlineStyle('overflow: hidden'), true);
  assert.equal(hasBoxAffectingInlineStyle('width:50px'), true);
  // NOT box-affecting — paint-only / inherited text properties.
  assert.equal(hasBoxAffectingInlineStyle('color: red; background: green'), false);
  assert.equal(hasBoxAffectingInlineStyle('font-size: 2em'), false);
  // Prefix matching must not fire on an unrelated property that merely
  // starts with the same letters (`border` vs `border-spacing` is a real
  // prefix and SHOULD fire; `overflow-wrap` is a text property but is
  // accepted by design — over-adopting a wrapper is the cheap direction).
  assert.equal(hasBoxAffectingInlineStyle('widows: 2'), false);
  assert.equal(hasBoxAffectingInlineStyle('topical: x'), false);
  // Degenerate inputs never throw and never adopt.
  assert.equal(hasBoxAffectingInlineStyle(''), false);
  assert.equal(hasBoxAffectingInlineStyle(null), false);
  assert.equal(hasBoxAffectingInlineStyle('not-a-declaration'), false);
});

test('N1: adoptsUnclosedBody needs BOTH an element body and a load-bearing style', () => {
  const body = '<div style="margin-left:-100px"><table></table>';
  const open = '<div style="margin-left:-100px">';
  const from = open.length;
  // Both conditions hold.
  assert.equal(adoptsUnclosedBody(open, body, from, body.length), true);
  // No element in the adopted body — adoption would change no structure.
  const textOnly = `${open}just text`;
  assert.equal(adoptsUnclosedBody(open, textOnly, from, textOnly.length), false);
  // Empty body.
  assert.equal(adoptsUnclosedBody(open, open, from, open.length), false);
  // Not load-bearing: no style attribute at all, or a paint-only one.
  const plain = '<div class=container>';
  const plainBody = `${plain}<table></table>`;
  assert.equal(adoptsUnclosedBody(plain, plainBody, plain.length, plainBody.length), false);
  const paint = '<div style="color:red">';
  const paintBody = `${paint}<table></table>`;
  assert.equal(adoptsUnclosedBody(paint, paintBody, paint.length, paintBody.length), false);
  // Single-quoted and unquoted style attributes parse the same.
  const sq = "<div style='width:10px'>";
  const sqBody = `${sq}<i></i>`;
  assert.equal(adoptsUnclosedBody(sq, sqBody, sq.length, sqBody.length), true);
});

test('N1: css-tables/absolute-tables-010 — the abspos table keeps its wrapper', () => {
  // The real shape: ONE `</div>` for TWO `<div>`s, so the outer div's
  // close-pairing consumes it and the inner -100px wrapper is left unclosed.
  // Pre-wave-33 the wrapper came out EMPTY and the <table> became its
  // SIBLING, which deletes the parent link the abspos static position
  // (CSS 2.1 §10.3.7) is computed from.
  const html = '<body><div class="container" style="margin-left: 100px;">'
    + '<div style="margin-left: -100px;"><table><td>X</td></table></div></body>';
  const tree = extractBodyTreeNested(html, 5, null);
  assert.equal(tree.length, 1);
  const container = tree[0];
  assert.equal(container.children.length, 1, 'the table must NOT be a sibling');
  const wrapper = container.children[0];
  assert.equal(wrapper.attrs.style, 'margin-left: -100px;');
  assert.equal(wrapper.children.length, 1);
  assert.equal(wrapper.children[0].tag, 'table');
});

test('N1: a NON-load-bearing unclosed wrapper still flattens (the depth budget)', () => {
  // text-transform-capitalize-035's shape — six `<div lang=…>` blocks each
  // terminated by a typo'd `<div>`. Adopting these nests 12 deep, past the
  // maxDepth-5 walk, where the tail is silently dropped. Flat-but-complete
  // beats nested-but-truncated, so a wrapper with no box-affecting INLINE
  // style keeps the pre-wave-33 emission.
  const html = '<body><div lang=ca><span>a</span><div><div lang=sc><span>b</span><div></body>';
  const tree = extractBodyTreeNested(html, 5, null);
  const tags = tree.map((n) => n.tag);
  assert.deepEqual(tags, ['div', 'span', 'div', 'div', 'span', 'div']);
});

test('N1: the own-text scanner adopts on the SAME gate as the tree walker', () => {
  // Both walkers must agree about where an unclosed child ends, or the
  // child's text is counted twice — once as the parent's own text (scanner
  // falling through) and once as the adopted child's (tree walk).
  // css-break/inline-skipping-fragmentainer-001's `<span
  // style="position:relative">` is the measured case.
  const inner = '<span style="position:relative;">TEXT<div style="width:50px"></div>';
  assert.equal(extractOwnText(inner), '', 'the span owns TEXT, not its parent');
  // Without a load-bearing style the scanner keeps its legacy fallthrough,
  // exactly matching walkChildren's zero-length emission.
  assert.equal(extractOwnText('<span>TEXT<div></div>'), 'TEXT');
});

// ── wave-33 lane N (N2): the rightmost-unsupported merge-guard hole ──────────

test('N2: leadingTypeSelector is anchored, not a scan', () => {
  assert.equal(leadingTypeSelector('span[hidden]'), 'span');
  assert.equal(leadingTypeSelector('B:hover'), 'b');
  assert.equal(leadingTypeSelector('em::first-line'), 'em');
  // No leading type selector — nothing to protect.
  assert.equal(leadingTypeSelector('.note[hidden]'), null);
  assert.equal(leadingTypeSelector('#x[hidden]'), null);
  assert.equal(leadingTypeSelector('*[hidden]'), null);
  assert.equal(leadingTypeSelector(':hover'), null);
  assert.equal(leadingTypeSelector(''), null);
  assert.equal(leadingTypeSelector(null), null);
});

test('N2: an unsupported RIGHTMOST compound still guards its tag', () => {
  // The two diagnosed shapes. Pre-wave-33 both contributed nothing, so the
  // styled element was inline-merged away and the declarations became
  // undeliverable by ANY channel.
  assert.deepEqual([...collectStyledTags([{ selector: 'span[hidden]' }])], ['span']);
  assert.deepEqual([...collectStyledTags([{ selector: 'b:hover' }])], ['b']);
  // The tag is taken from the RIGHTMOST compound only — an ancestor tag in
  // the chain is not protected (it needs element descendants to match, and
  // a mergeable child is text-only by definition).
  assert.deepEqual([...collectStyledTags([{ selector: 'div span[hidden]' }])], ['span']);
  // Nothing identifiable → nothing added (no widening to the A2b raw scan).
  assert.deepEqual([...collectStyledTags([{ selector: '.note:hover' }])], []);
  assert.deepEqual([...collectStyledTags([{ selector: '*[hidden]' }])], []);
  // Supported rules are unchanged.
  assert.deepEqual([...collectStyledTags([{ selector: 'code' }])], ['code']);
});

test('N2: the guarded span survives the inline merge', () => {
  // selectors-4/lang-021's shape: `span:lang(...)` is unsupported, and the
  // inner <span> is attribute-free text-only — exactly what
  // isPureInlineMergeable absorbs. Keeping it is the whole point of the test.
  const rules = parseCss('span:lang("*-gb") { color: green }');
  const styled = collectStyledTags(rules);
  assert.equal(styled.has('span'), true);
  assert.equal(isPureInlineMergeable('span', {}, 'green text', styled), false);
  // …and with the guard empty (pre-fix behaviour) it would have merged.
  assert.equal(isPureInlineMergeable('span', {}, 'green text', new Set()), true);
});

// ── wave-34 lane F2 (F2a): the @font-face SCAN ───────────────────────────────
//
// Self-contained import block (not folded into the file's top list) because
// this region is owned by a different lane than the _runs/text regions above
// — ESM hoists it identically, and a separate statement cannot conflict.
import {
  scanFontFaces,
  resolveFontFaces,
  wptRelativePath,
} from './extract-fixture.mjs';
import { join as joinPath } from 'node:path';
import { fileURLToPath as fileUrlToPath } from 'node:url';

// The corpus root the resolver measures against — same derivation the
// module uses, so these tests read the real mirror when it is present.
const F2_REPO_ROOT = joinPath(fileUrlToPath(import.meta.url), '..', '..', '..');
const F2_WPT_DIR = process.env.WPT_DIR ?? joinPath(F2_REPO_ROOT, 'tools', 'wpt');

test('F2a: scanFontFaces reads family/src/weight/style off a block', () => {
  // The css-text/boundary-shaping-001 shape, verbatim: this is the family
  // the whole wall is named after.
  const faces = scanFontFaces(
    '@font-face {\n  font-family: test;\n' +
    '  src: url(resources/LinLibertine_Re-4.7.5.woff);\n}\n' +
    'body { font: 36px test; }');
  assert.equal(faces.length, 1);
  assert.equal(faces[0].family, 'test');
  assert.equal(faces[0].src, 'resources/LinLibertine_Re-4.7.5.woff');
  // Omitted descriptors stay null — the css-fonts-4 §4.4/§4.5 initial is the
  // CONSUMER's to apply; inventing "normal" here would make "omitted" and
  // "explicitly normal" indistinguishable downstream.
  assert.equal(faces[0].weight, null);
  assert.equal(faces[0].style, null);
});

test('F2a: quoted and unquoted family spellings normalise identically', () => {
  // css-fonts-4 §4.2 lets the descriptor be a <string> or a <custom-ident>;
  // both name the SAME family, so a consumer must never re-tokenise.
  for (const spelling of ['test', '"test"', "'test'"]) {
    const [f] = scanFontFaces(`@font-face { font-family: ${spelling}; src: url(a.woff); }`);
    assert.equal(f.family, 'test', `spelling ${spelling}`);
  }
});

test('F2a: descriptors that CANNOT be normalised ride verbatim', () => {
  // A §4.4 weight RANGE has no numeric 100–900 equivalent and a §4.5
  // oblique ANGLE has no keyword one. Collapsing either would destroy the
  // face-matching input, so this reader is a courier for both.
  const [f] = scanFontFaces(
    "@font-face { font-family: X; src: url(a.otf); " +
    'font-weight: 400 700; font-style: oblique 20deg; }');
  assert.equal(f.weight, '400 700');
  assert.equal(f.style, 'oblique 20deg');
});

test('F2a: the FIRST url() arm of a font-src-list wins; local() is skipped', () => {
  // css-fonts-4 §4.3: arms are tried in order. A local() arm names an
  // INSTALLED system face, which this channel (which delivers FILES) cannot
  // carry — so it is skipped rather than recorded as a fake path.
  const [f] = scanFontFaces(
    '@font-face { font-family: X; ' +
    "src: local(Helvetica), url(first.woff2) format('woff2'), url(second.woff); }");
  assert.equal(f.src, 'first.woff2');
  // A local()-ONLY block yields src: null (and resolveFontFaces drops it).
  const [only] = scanFontFaces('@font-face { font-family: Y; src: local(Arial); }');
  assert.equal(only.src, null);
});

test('F2a: a nameless face is DROPPED, never guessed', () => {
  // §4.1 makes font-family required; a face with no name is unreferenceable,
  // so emitting one would be inventing a font.
  assert.deepEqual(scanFontFaces('@font-face { src: url(a.woff); }'), []);
});

test('F2a: multiple blocks keep DOCUMENT order', () => {
  // §4.1: a later face with the same (family, weight, style) WINS, so order
  // is payload — a reordering scan would silently change which file renders.
  const faces = scanFontFaces(
    '@font-face { font-family: A; src: url(1.woff); }' +
    '@font-face { font-family: B; src: url(2.woff); }' +
    '@font-face { font-family: A; src: url(3.woff); }');
  assert.deepEqual(faces.map((f) => `${f.family}:${f.src}`),
    ['A:1.woff', 'B:2.woff', 'A:3.woff']);
});

test('F2a: the block walk survives braces and semicolons inside values', () => {
  // A naive `body.split(';')` shreds `url(a;b.woff)`; a regex-only block
  // finder loses the matching '}' the moment a descriptor carries one.
  const [f] = scanFontFaces(
    '@font-face { font-family: X; src: url("a;b.woff"); ' +
    'unicode-range: U+0-7F; }\n.after { color: red }');
  assert.equal(f.src, 'a;b.woff');
  // …and the rule AFTER the block is not swallowed into it.
  const two = scanFontFaces(
    '@font-face { font-family: X; src: url(a.woff) }\n' +
    '@media screen { p { color: red } }\n' +
    '@font-face { font-family: Y; src: url(b.woff) }');
  assert.deepEqual(two.map((f2) => f2.family), ['X', 'Y']);
});

test('F2a: wptRelativePath declines anything outside the corpus', () => {
  // Containment is checked on the RESOLVED path — the author's token is
  // untrusted third-party text, and a prefix test on it would miss `..`.
  assert.equal(wptRelativePath(joinPath(F2_WPT_DIR, 'css', 'x.woff')), 'css/x.woff');
  assert.equal(wptRelativePath(joinPath(F2_WPT_DIR, '..', 'secrets.woff')), null);
  assert.equal(wptRelativePath(F2_WPT_DIR), null);
});

test('F2a: resolveFontFaces drops every undeliverable arm', async () => {
  const base = joinPath(F2_WPT_DIR, 'css', 'css-text', 'boundary-shaping');
  const out = await resolveFontFaces([
    { family: 'a', src: null, weight: null, style: null },                       // local()-only
    { family: 'b', src: 'data:font/ttf;base64,AA', weight: null, style: null },  // already inline
    { family: 'c', src: 'https://x.example/f.woff', weight: null, style: null }, // remote
    { family: 'd', src: '//cdn.example/f.woff', weight: null, style: null },     // protocol-relative
    { family: 'e', src: '{{host}}/f.woff', weight: null, style: null },          // WPT template
    { family: 'f', src: '../../../../escape.woff', weight: null, style: null },  // escapes corpus
    { family: 'g', src: 'resources/cat.png', weight: null, style: null },        // not a font
    { family: 'h', src: 'resources/absent.woff', weight: null, style: null },    // not on disk
  ], base);
  // Every arm above is undeliverable for a DIFFERENT stated reason, and none
  // may reach the wire: a path to a missing file is a fabricated fact.
  assert.deepEqual(out, []);
});

test('F2a: resolveFontFaces emits a corpus-relative path, de-duplicated', async (t) => {
  const base = joinPath(F2_WPT_DIR, 'css', 'css-text', 'boundary-shaping');
  const rel = 'resources/LinLibertine_Re-4.7.5.woff';
  const probe = await resolveFontFaces([{ family: 'test', src: rel, weight: null, style: null }], base);
  if (probe.length === 0) {
    // The mirror is gitignored (tools/titan/fetch-wpt.sh populates it), so
    // skip rather than fail on a corpus-less checkout — the pure half above
    // already pins the reader.
    t.skip('WPT corpus mirror not present');
    return;
  }
  assert.deepEqual(probe, [{
    family: 'test',
    src: 'css/css-text/boundary-shaping/resources/LinLibertine_Re-4.7.5.woff',
  }]);
  // Same face declared twice (the per-@media-arm shape WPT sheets use) is
  // ONE wire entry: a duplicate @font-face is a no-op in CSS but a doubled
  // payload here.
  const dupes = await resolveFontFaces([
    { family: 'test', src: rel, weight: null, style: null },
    { family: 'test', src: rel, weight: null, style: null },
    { family: 'test', src: rel, weight: '700', style: null },   // different key — kept
  ], base);
  assert.equal(dupes.length, 2);
  assert.equal(dupes[1].weight, '700');
  // Omit-when-absent on the wire: no weight/style keys at all when the
  // author declared none.
  assert.deepEqual(Object.keys(dupes[0]), ['family', 'src']);
});

// ── wave-34 lane R: `:has()`, the relational pseudo-class ───────────────────
//
// Selectors-4 §5.4. Every assertion below is anchored to a real corpus test
// (named in its comment), because the whole point of the lane was to stop
// paying a browser page-load to answer questions about static markup.

test('lane-r34: :has(> X) — the child form (has-style-sharing-001)', () => {
  // `:has(> span) { background: green }` over `<div><span></span></div>` +
  // a bare `<div></div>`. The FIRST div goes green, the second does not —
  // that is the entire assertion of has-style-sharing-001/-002.
  const css = 'div { background: blue } :has(> span) { background: green } '
            + 'span { display: inline-block }';
  const { components } = buildComponents(
    '<body><div><span></span></div><div></div></body>', parseCss(css), 'h');
  assert.equal(components.h__0.properties.background, 'green');
  assert.equal(components.h__1.properties.background, 'blue');
});

test('lane-r34: the child form does NOT reach a grandchild', () => {
  // `:scope > .a` is exact — a `.a` one level deeper must not satisfy it,
  // or `:has(>)` would silently become the descendant form.
  const css = ':has(> .a) { background: green } b { color: red }';
  const { components } = buildComponents(
    '<body><div><b><i class="a"></i></b></div></body>', parseCss(css), 'g');
  assert.equal(components.g__0.properties.background, undefined);
});

test('lane-r34: the default (descendant) form reaches any depth', () => {
  const css = ':has(.a) { background: green } b { color: red } i { color: red }';
  const { components } = buildComponents(
    '<body><div><b><i class="a"></i></b></div></body>', parseCss(css), 'd');
  assert.equal(components.d__0.properties.background, 'green');
});

test('lane-r34: the sibling forms look FORWARD, not backward', () => {
  // `:has(+ X)` / `:has(~ X)` put the SUBJECT on the left of the combinator —
  // the mirror image of the `A + B` sibling steps in selectorMatchesPseudoElement.
  const css = '.p:has(+ .q) { color: green } .r:has(~ .t) { color: blue } p { margin: 0 }';
  const { components } = buildComponents(
    '<body><p class="p"></p><p class="q"></p><p class="r"></p><p></p><p class="t"></p></body>',
    parseCss(css), 's');
  assert.equal(components.s__0.properties.color, 'green');   // .p + .q  ✓
  assert.equal(components.s__2.properties.color, 'blue');    // .r ~ .t  ✓ (gap of one)
  // …and the backward reading must NOT fire: `.q` precedes nothing matching.
  assert.equal(components.s__1.properties.color, undefined);
});

test('lane-r34: adjacency is exact for `+`', () => {
  const css = '.p:has(+ .t) { color: green } p { margin: 0 }';
  const { components } = buildComponents(
    '<body><p class="p"></p><p></p><p class="t"></p></body>', parseCss(css), 'x');
  assert.equal(components.x__0.properties.color, undefined);
});

test('lane-r34: a MERGE-ABSORBED child is still a DOM element to :has()', () => {
  // selectors/dir-pseudo-in-has.html. The stylesheet targets no `span`, so
  // wave-12's inline merge absorbs it and the div ships with NO children —
  // but the browser's `:has()` sees it. Answering off the component tree
  // would paint the div red, CONFIDENTLY (a parse-supported `:has()` no
  // longer earns the post-load browser that used to cover this test).
  const css = 'div { background: red } .ltr:has(*:dir(ltr)) { background: green }';
  const { components } = buildComponents(
    '<body><div class="ltr"><span></span></div></body>', parseCss(css), 'm');
  assert.equal(components.m__0.properties.background, 'green');
  // Proof the span really was absorbed (i.e. the test is testing what it says).
  assert.equal(components.m__0.children, undefined);
});

test('lane-r34: :dir() inside :has() inherits through the SUBJECT', () => {
  // The candidate's ancestor chain must be extended with the subject and
  // every element walked through, or `<div dir=rtl><span></span></div>`
  // would answer ltr for the span and paint the .rtl case red.
  const css = 'div { background: red } .rtl:has(*:dir(rtl)) { background: green }';
  const { components } = buildComponents(
    '<body><div dir="rtl" class="rtl"><span></span></div></body>', parseCss(css), 'r');
  assert.equal(components.r__0.properties.background, 'green');
});

test('lane-r34: :not(:has(…)) is evaluated, not inverted blindly', () => {
  // selectors/has-style-sharing-007: `.special.cousin:not(:has(span))`.
  const css = '.cousin { background: purple } .special.cousin:not(:has(span)) { background: blue }';
  const { components } = buildComponents(
    '<body><div class="cousin special"><span></span></div>'
    + '<div class="cousin special"></div></body>', parseCss(css), 'n');
  assert.equal(components.n__0.properties.background, 'purple'); // has a span → not() false
  assert.equal(components.n__1.properties.background, 'blue');   // no span   → not() true
});

test('lane-r34: an UNDECIDABLE :has() inside :not() drops the rule', () => {
  // The inversion hazard: compoundMatches folds "cannot decide" into `false`,
  // and `!false` is a confident match. With no relational metadata at all
  // (a legacy direct call site, or a maxDepth-truncated subtree) the answer
  // must be null — rule dropped — never `true`.
  assert.equal(selectorMatches('div:not(:has(span))', 'div', {}), false);
  // The positive form is equally refused (never a silent "no descendant").
  assert.equal(selectorMatches('div:has(span)', 'div', {}), false);
});

test('lane-r34: the refused argument shapes stay unsupported (post-load route)', () => {
  // Each of these must still COUNT as a dropped rule, which is what routes
  // the test to the live browser instead of a guess.
  assert.equal(countUnsupportedRules(parseCss(
    'div:has(~ .item > :nth-child(2)) { color: red }')), 1);   // complex relative selector
  assert.equal(countUnsupportedRules(parseCss(
    'div:has(:has(span)) { color: red }')), 1);                 // nested :has()
  assert.equal(countUnsupportedRules(parseCss(
    'div:has(span::before) { color: red }')), 1);               // pseudo-element in the arg
  assert.equal(countUnsupportedRules(parseCss(
    'div:has() { color: red }')), 1);                           // empty argument
  assert.equal(countUnsupportedRules(parseCss(
    'div:has([hidden]) { color: red }')), 1);                    // attr selector in the arg
  // …while the supported shapes no longer count.
  assert.equal(countUnsupportedRules(parseCss(
    'div:has(> span) { color: red } .a:has(+ .b) { color: red } p:has(i) { color: red }')), 0);
});

test('lane-r34: :has(> X) inside an `of S` clause renumbers correctly', () => {
  // selectors/nth-child-of-has.html — `div:nth-child(even of :has(span))`.
  // The `of S` filter matches each SIBLING through a minimal pos, so the
  // relational handle has to ride on the sibling entry too or the filtered
  // set comes back empty and the rule silently applies to nothing.
  const css = 'div { background: red } div:nth-child(even of :has(span)) { background: green }';
  const html = '<body><div class="c">'
    + '<div></div>'                 // 1 — no span
    + '<div><span></span></div>'    // 2 — span #1  → filtered index 1 (odd)
    + '<div></div>'                 // 3
    + '<div><span></span></div>'    // 4 — span #2  → filtered index 2 (EVEN) ✓
    + '</div></body>';
  const { components } = buildComponents(html, parseCss(css), 'o');
  const kids = components.o__0.children;
  const ids = Object.keys(kids);
  assert.equal(kids[ids[1]].properties.background, 'red');
  assert.equal(kids[ids[3]].properties.background, 'green');
});

test('lane-r34: :has() on an ANCESTOR compound is declined, not evaluated', () => {
  // The measured line (see the branch in parseCompound): `:has(> .a) .b`
  // is evaluable but its cascade is not — our matcher has no specificity, so
  // the later `.b { purple }` would beat the green Chromium paints. Declining
  // keeps the rule counted, which keeps the test on the post-load browser.
  assert.equal(countUnsupportedRules(parseCss(
    ':has(> .a) .b { background: green } .b { background: purple }')), 1);
  const { components } = buildComponents(
    '<body><div><span class="b"></span><span class="a"></span></div></body>',
    parseCss(':has(> .a) .b { background: green } .b { background: purple } span { display: inline-block }'),
    'p');
  const kids = components.p__0.children;
  assert.equal(kids[Object.keys(kids)[0]].properties.background, 'purple');
  // …while the SUBJECT-side form of the very same relation is applied.
  assert.equal(countUnsupportedRules(parseCss('.b:has(> .a) { background: green }')), 0);
});

// ── wave-34 lane R: the stale-run sweep (the bidi-bake interaction) ─────────

const { dropStaleRuns } = await import('./extract-fixture.mjs');

test('lane-r34: dropStaleRuns removes a run list whose text was dissolved', () => {
  // The shape bidi-bake's applyBidiBakePlan leaves behind: `_text` deleted,
  // the content re-emitted as absolutely-positioned children, `_runs` still
  // naming a text run that no longer exists on the component. Painting it
  // would double the glyphs on top of the positioned boxes — MEASURED at
  // −0.0111 SSIM on css-text/boundary-shaping-009.
  const fixture = {
    components: {
      baked: {
        properties: { position: 'relative' },
        _runs: [{ text: 'السلام' }, { child: 'baked__0' }],
        children: {
          baked__0: { id: 'baked__0', properties: { position: 'absolute', left: '100px' } },
        },
      },
      // …and an UNBAKED neighbour, whose pair is intact, must be untouched.
      intact: {
        properties: {},
        _text: 'the quick',
        _runs: [{ text: 'the quick ' }, { child: 'intact__0' }],
        children: { intact__0: { id: 'intact__0', _text: 'brown' } },
      },
    },
  };
  assert.equal(dropStaleRuns(fixture), 1);
  assert.equal(fixture.components.baked._runs, undefined);
  assert.deepEqual(fixture.components.intact._runs,
    [{ text: 'the quick ' }, { child: 'intact__0' }]);
  // Idempotent, and null-safe for the ref-less half of a fixture pair.
  assert.equal(dropStaleRuns(fixture), 0);
  assert.equal(dropStaleRuns(undefined), 0);
  assert.equal(dropStaleRuns({}), 0);
});

test('lane-r34: the sweep reaches nested children', () => {
  const fixture = {
    components: {
      a: {
        properties: {},
        _text: 'x',
        children: { a__0: { id: 'a__0', _runs: [{ text: 'y' }, { child: 'a__0__0' }] } },
      },
    },
  };
  assert.equal(dropStaleRuns(fixture), 1);
  assert.equal(fixture.components.a.children.a__0._runs, undefined);
});

// ═════════════════════════════════════════════════════════════════════════════
// wave-36 M6 — HTML §15.3.3 TABLE PRESENTATIONAL ATTRIBUTES
//
// Every expectation below was READ OFF the ref pipeline's own headless
// Chromium (_diag36/M6/probe-attrs.mjs), not off the spec prose, because the
// acceptance target is that browser's render and Blink's
// HTMLTableElement::ParseBorderWidthAttribute diverges from a naive reading
// of "rules for parsing non-negative integers" on exactly the spellings the
// corpus uses (`border=""`, `border="border"`, `border="yes"`, `border="-3"`).
// ═════════════════════════════════════════════════════════════════════════════

const {
  parseHtmlNonNegativeInteger,
  tableBorderAttrWidthPx,
  tableCellPaddingPx,
  nearestTableAncestor,
  htmlTablePresentationProps,
} = await import('./extract-fixture.mjs');

test('wave36 M6: parseHtmlNonNegativeInteger follows HTML §2.4.4.2', () => {
  assert.equal(parseHtmlNonNegativeInteger('0'), 0);
  assert.equal(parseHtmlNonNegativeInteger('1'), 1);
  assert.equal(parseHtmlNonNegativeInteger('+7'), 7);
  // Trailing junk is IGNORED, not an error — `border="3px"` renders 3px.
  assert.equal(parseHtmlNonNegativeInteger('3px'), 3);
  assert.equal(parseHtmlNonNegativeInteger('1.9'), 1);
  // Leading ASCII whitespace is skipped; NBSP is not ASCII whitespace.
  assert.equal(parseHtmlNonNegativeInteger('  \t5'), 5);
  assert.equal(parseHtmlNonNegativeInteger(' 5'), null);
  // Errors: empty, negative, non-numeric.
  assert.equal(parseHtmlNonNegativeInteger(''), null);
  assert.equal(parseHtmlNonNegativeInteger('-3'), null);
  assert.equal(parseHtmlNonNegativeInteger('border'), null);
  assert.equal(parseHtmlNonNegativeInteger(undefined), null);
});

test('wave36 M6: tableBorderAttrWidthPx mirrors Blink on every corpus spelling', () => {
  // Measured in the ref's Chromium — see the block banner.
  assert.equal(tableBorderAttrWidthPx({}), 0);                    // no attribute
  assert.equal(tableBorderAttrWidthPx({ border: '0' }), 0);       // explicit zero
  assert.equal(tableBorderAttrWidthPx({ border: '1' }), 1);       // the 8 print tests
  assert.equal(tableBorderAttrWidthPx({ border: '2' }), 2);
  assert.equal(tableBorderAttrWidthPx({ border: '5' }), 5);
  assert.equal(tableBorderAttrWidthPx({ border: '3px' }), 3);
  // Present-but-unparseable is 1px, NOT 0 — attribute PRESENCE decides.
  assert.equal(tableBorderAttrWidthPx({ border: '' }), 1);
  assert.equal(tableBorderAttrWidthPx({ border: 'border' }), 1);
  assert.equal(tableBorderAttrWidthPx({ border: 'yes' }), 1);
  assert.equal(tableBorderAttrWidthPx({ border: '-3' }), 1);
  assert.equal(tableBorderAttrWidthPx({ border: '1.9' }), 1);
  // css-tables/html-to-css-mapping-2's two-billion value clamps like Blink's.
  assert.equal(tableBorderAttrWidthPx({ border: '2026722966' }), 33554400);
});

test('wave36 M6: tableCellPaddingPx defaults to the 1px `revert` cannot restore', () => {
  assert.equal(tableCellPaddingPx({}), 1);
  assert.equal(tableCellPaddingPx({ cellpadding: '0' }), 0);
  assert.equal(tableCellPaddingPx({ cellpadding: '7' }), 7);
  // Unparseable keeps Blink's seeded default rather than collapsing to 0.
  assert.equal(tableCellPaddingPx({ cellpadding: 'x' }), 1);
});

test('wave36 M6: nearestTableAncestor picks the INNER table', () => {
  const outer = { tag: 'table', attrs: { border: '5' } };
  const inner = { tag: 'table', attrs: { border: '1' } };
  assert.equal(nearestTableAncestor([outer, { tag: 'tr', attrs: {} },
    { tag: 'td', attrs: {} }, inner, { tag: 'tr', attrs: {} }]), inner);
  assert.equal(nearestTableAncestor([{ tag: 'div', attrs: {} }]), null);
  assert.equal(nearestTableAncestor(null), null);
});

test('wave36 M6: <table border=1> bakes the outset frame and the inset cell rules', () => {
  const table = { tag: 'table', attrs: { border: '1' } };
  // The table box: 1px outset on all four sides, no padding, no spacing.
  assert.deepEqual(htmlTablePresentationProps('table', table.attrs, [], {}), {
    unmodelled: false,
    props: {
      'border-top-width': '1px', 'border-top-style': 'outset',
      'border-right-width': '1px', 'border-right-style': 'outset',
      'border-bottom-width': '1px', 'border-bottom-style': 'outset',
      'border-left-width': '1px', 'border-left-style': 'outset',
    },
  });
  // A cell of that table: 1px INSET (never the table's width) + 1px padding.
  const cell = htmlTablePresentationProps('td', {},
    [table, { tag: 'tbody', attrs: {} }, { tag: 'tr', attrs: {} }], {});
  assert.equal(cell.props['border-left-width'], '1px');
  assert.equal(cell.props['border-left-style'], 'inset');
  assert.equal(cell.props['padding-left'], '1px');
  // …and a wide frame does NOT widen the cell rules (the spec's asymmetry).
  const wide = htmlTablePresentationProps('td', {},
    [{ tag: 'table', attrs: { border: '5' } }, { tag: 'tr', attrs: {} }], {});
  assert.equal(wide.props['border-top-width'], '1px');
  assert.equal(
    htmlTablePresentationProps('table', { border: '5' }, [], {})
      .props['border-top-width'], '5px');
});

test('wave36 M6: a bare <table> still gives its cells the 1px UA padding', () => {
  const cell = htmlTablePresentationProps('td', {},
    [{ tag: 'table', attrs: {} }, { tag: 'tr', attrs: {} }], {});
  assert.deepEqual(cell.props, {
    'padding-top': '1px', 'padding-right': '1px',
    'padding-bottom': '1px', 'padding-left': '1px',
  });
  // …and nothing at all for the table box itself.
  assert.equal(htmlTablePresentationProps('table', {}, [], {}), null);
  // A cell with no table ancestor gets nothing rather than a guessed default.
  assert.equal(htmlTablePresentationProps('td', {}, [{ tag: 'div', attrs: {} }], {}), null);
  // Non-table boxes are never touched — rows and row groups included.
  assert.equal(htmlTablePresentationProps('tr', {},
    [{ tag: 'table', attrs: { border: '1' } }], {}), null);
  assert.equal(htmlTablePresentationProps('div', {}, [], {}), null);
});

test('wave36 M6: cellpadding / cellspacing override the defaults', () => {
  const t = { tag: 'table', attrs: { cellpadding: '0', cellspacing: '10' } };
  assert.deepEqual(htmlTablePresentationProps('table', t.attrs, [], {}).props,
    { 'border-spacing': '10px' });
  // cellpadding=0 is a real zero, not "absent" — the cell fill still emits.
  assert.deepEqual(htmlTablePresentationProps('td', {}, [t, { tag: 'tr', attrs: {} }], {}).props,
    {
      'padding-top': '0px', 'padding-right': '0px',
      'padding-bottom': '0px', 'padding-left': '0px',
    });
  // No cellspacing attribute → the UA 2px default is NOT emitted (scope cut).
  assert.equal(htmlTablePresentationProps('table', { cellpadding: '3' }, [], {}), null);
});

test('wave36 M6: an author declaration beats the presentation hint', () => {
  const t = { tag: 'table', attrs: { border: '1' } };
  const chain = [t, { tag: 'tr', attrs: {} }];
  // Any padding spelling suppresses the WHOLE padding fill; the border fill
  // is independent and still lands.
  const padDecided = htmlTablePresentationProps('td', {}, chain, { 'padding-inline': '4px' });
  assert.equal(padDecided.props['padding-top'], undefined);
  assert.equal(padDecided.props['border-top-style'], 'inset');
  // Any border spelling suppresses the WHOLE border fill; padding survives.
  const borderDecided = htmlTablePresentationProps('td', {}, chain, { border: '2px dashed red' });
  assert.equal(borderDecided.props['border-top-width'], undefined);
  assert.equal(borderDecided.props['padding-top'], '1px');
  // A declared border-spacing wins over cellspacing.
  assert.equal(htmlTablePresentationProps('table', { cellspacing: '10' }, [],
    { 'border-spacing': '3px' }), null);
  // border-COLOR alone leaves the UA width/style in force (not a guard).
  assert.equal(htmlTablePresentationProps('table', { border: '1' }, [],
    { 'border-color': 'red' }).props['border-top-style'], 'outset');
});

test('wave36 M6: rules/frame/bordercolor decline the whole bake, loudly', () => {
  for (const attr of ['rules', 'frame', 'bordercolor']) {
    const t = { tag: 'table', attrs: { border: '1', [attr]: 'x' } };
    const onTable = htmlTablePresentationProps('table', t.attrs, [], {});
    assert.deepEqual(onTable, { props: {}, unmodelled: true },
      `${attr} must decline on the table`);
    // The decline propagates to the cells — no half-modelled border box.
    assert.deepEqual(
      htmlTablePresentationProps('td', {}, [t, { tag: 'tr', attrs: {} }], {}),
      { props: {}, unmodelled: true }, `${attr} must decline on the cells`);
  }
});

test('wave36 M6: buildComponents emits the frame end-to-end for the 8 print tests', () => {
  // The exact markup of CSS2/pagination/rowgroup-page-break-inside-avoid-4-print.
  const html = '<table border="1"><tbody><tr><td><p>2</p></td></tr></tbody></table>';
  const { components } = buildComponents(html, [], 'x');
  const flat = [];
  (function walk(map) {
    for (const c of Object.values(map ?? {})) {
      flat.push(c);
      if (c.children) walk(c.children);
    }
  })(components);
  const table = flat.find((c) => c._tag === 'table');
  const td = flat.find((c) => c._tag === 'td');
  assert.equal(table.properties['border-top-style'], 'outset');
  assert.equal(table.properties['border-top-width'], '1px');
  assert.equal(td.properties['border-top-style'], 'inset');
  assert.equal(td.properties['padding-top'], '1px');
  // …and both wear the loud provenance marker.
  assert.ok(table._lossyReasons.includes('html-table-presentation-baked'));
  assert.ok(td._lossyReasons.includes('html-table-presentation-baked'));
});

// ═════════════════════════════════════════════════════════════════════════════
// wave-36 lane M3: QUOTES + GENERATED CONTENT
//   M3-a  the LEGACY ONE-COLON pseudo-element notation (Selectors-3 §7.1)
//   M3-b  `quotes` joins the root-inherited bake-down (css-content-3 §2.2)
// ═════════════════════════════════════════════════════════════════════════════

// ── M3-a: `:before` / `:after` are the CSS1/CSS2 spelling of the same
// pseudo-elements `::before` / `::after` name. Selectors-3 §7.1 REQUIRES a
// UA to accept both. parseCompound implemented only `::`, so a single colon
// fell through to the pseudo-CLASS branch, came back `unsupported`, and the
// whole rule was dropped before extraction — the generated box never existed
// on the wire (measured: 17 failing bucket-A cells across 8 sections, every
// one with capture ink 0.00% against a ref that paints the generated text).

test('wave36 M3-a: :before is the legacy spelling of ::before and lands in the same bucket', () => {
  // The exact shape of css/css-content/attr-case-sensitivity-001.html.
  const html = '<body><div id="gencon" foo="a"></div></body>';
  const rules = parseCss('div#gencon:before { content: attr(foo) }');
  const { components } = buildComponents(html, rules, 'legacy');
  const div = components['legacy__0'];
  assert.ok(div._pseudo, 'one-colon :before must build a _pseudo bucket');
  // attr() bakes against the HOST's attributes (wave-25 ATTR BAKE), exactly
  // as it does for the `::` spelling — the alias changes the SELECTOR only.
  assert.equal(div._pseudo.before.properties.content, '"a"');
  // …and the declaration must NOT leak onto the host box (CSS GC L3 §3.2).
  assert.equal(div.properties.content, undefined);
});

test('wave36 M3-a: :after resolves identically to ::after through the matcher', () => {
  // selectorMatchesPseudoElement returns the pseudo NAME on a match, so the
  // two spellings must return the same string for the same element.
  assert.equal(selectorMatchesPseudoElement('p:after', 'p', {}), 'after');
  assert.equal(selectorMatchesPseudoElement('p::after', 'p', {}), 'after');
  assert.equal(selectorMatchesPseudoElement('p:before', 'p', {}), 'before');
  // A non-matching host still returns null, not a bare pseudo name.
  assert.equal(selectorMatchesPseudoElement('div:before', 'p', {}), null);
});

test('wave36 M3-a: the alias never grants support the :: spelling lacks', () => {
  // :first-line / :first-letter ARE grandfathered by Selectors-3 §7.1, but
  // they are not in SUPPORTED_PSEUDO_ELEMENTS — so both spellings must land
  // on the same honest refusal rather than the alias smuggling them in.
  assert.equal(selectorMatchesPseudoElement('p:first-line', 'p', {}), null);
  assert.equal(selectorMatchesPseudoElement('p::first-line', 'p', {}), null);
  assert.equal(selectorMatchesPseudoElement('p:first-letter', 'p', {}), null);
  // `::marker` is CSS-Pseudo-4 and has NO one-colon form — `:marker` is an
  // unknown pseudo-CLASS and must stay unsupported.
  assert.equal(selectorMatchesPseudoElement('li:marker', 'li', {}), null);
  assert.equal(selectorMatchesPseudoElement('li::marker', 'li', {}), 'marker');
});

test('wave36 M3-a: the rightmost-token rule (Selectors-4 §3.3) holds for the alias', () => {
  // Anything trailing the pseudo name inside the compound is invalid for the
  // `::` spelling and must be equally invalid for the one-colon one.
  assert.equal(selectorMatchesPseudoElement('p:before.x', 'p', { class: 'x' }), null);
  assert.equal(selectorMatchesPseudoElement('p::before.x', 'p', { class: 'x' }), null);
  // A functional argument is not a thing any CSS1/2 pseudo-element takes.
  assert.equal(selectorMatchesPseudoElement('p:before(2)', 'p', {}), null);
  // Real pseudo-CLASSES whose names merely START with the same letters keep
  // working — the alias is an exact-name set, not a prefix test.
  assert.equal(selectorMatches('p:first-child', 'p', {}, null, { sibIndex: 0, siblings: ['p'] }), true);
});

test('wave36 M3-a: a one-colon pseudo-element rule stops counting as unsupported', () => {
  // countUnsupportedRules is the post-load activation trigger (wave-30 A3).
  // A rule we can now apply statically must no longer buy a browser page
  // load — that is the whole point of modelling it.
  assert.equal(countUnsupportedRules(parseCss('div:before { content: "x" }')), 0);
  assert.equal(countUnsupportedRules(parseCss('div::before { content: "x" }')), 0);
  // …while the genuinely unmodelled ones still do.
  assert.equal(countUnsupportedRules(parseCss('div:first-line { color: red }')), 1);
  assert.equal(countUnsupportedRules(parseCss('div:hover { color: red }')), 1);
});

// ── M3-b: `quotes` (css-content-3 §2.2) joins ROOT_INHERITED_TRIGGER_PROPS.
// `body { quotes: none }` sat on the body-root bag while the `<p>`/`<q>`
// components are its SIBLINGS on the wire, so the declaration reached
// nothing and every capture painted the initial `auto` curly marks
// (quotes-032 0.8822, quotes-031 0.8701 in wave36-M3-base).

test('wave36 M3-b: a root `quotes` declaration arms the inherited-bake trigger', () => {
  assert.equal(bodyDeclaresInheritedProperty({ quotes: 'none' }), true);
  assert.equal(bodyDeclaresInheritedProperty({ quotes: '"‹" "›"' }), true);
  // Nothing else changed: a bag with no trigger property still answers false.
  assert.equal(bodyDeclaresInheritedProperty({ 'background-color': 'red' }), false);
});

test('wave36 M3-b: the root `quotes` value is baked onto top-level body children', () => {
  // quotes-032's exact shape: the declaration is on <body>, the <p> is a
  // sibling of the body-root component, and its <q> descendants inherit
  // through the renderer's own DOM once the top-level hop carries it.
  const html = '<body><p>One <q>two</q></p></body>';
  const rules = parseCss('body { quotes: none }');
  const { components } = buildComponents(html, rules, 'q32');
  const p = components['q32__0'];
  assert.equal(p.properties.quotes, 'none');
  assert.ok(p._lossyReasons.includes('body-inherited-baked'));
});

test('wave36 M3-b: a child that declares its own `quotes` is never overwritten', () => {
  // css-cascade-4 §7.3: the child's own declaration outranks a value handed
  // down from its parent. `quotes` has no covering shorthand, so the generic
  // per-longhand guard is the whole contract.
  const html = '<body><p class="inner">One <q>two</q></p></body>';
  const rules = parseCss('body { quotes: none } .inner { quotes: auto }');
  const { components } = buildComponents(html, rules, 'q33');
  assert.equal(components['q33__0'].properties.quotes, 'auto');
});


// ── wave-37 lane W4: THE LANG WIRE ──────────────────────────────────────────

test('W4 lang: the document element chain answers body-first, then html', () => {
  // The exact ladder documentDirectionality uses, and for the same reason —
  // <body> is the nearer ancestor of every component we emit.
  assert.equal(documentLanguage('<html lang="fr"><body>x'), 'fr');
  assert.equal(documentLanguage('<html lang="fr"><body lang="ja">x'), 'ja');
  assert.equal(documentLanguage('<body lang=\'zh-Hant\'>x'), 'zh-Hant');
  // Unquoted attribute values are how half the corpus writes them.
  assert.equal(documentLanguage('<html lang=en><body>x'), 'en');
  // Case + subtags survive verbatim — RFC 4647 matching happens at lookup.
  assert.equal(documentLanguage('<html lang="eN-Us">'), 'eN-Us');
  // No declaration anywhere is the "unknown" answer, not a guess.
  assert.equal(documentLanguage('<html><body>x'), null);
  assert.equal(documentLanguage(null), null);
});

test('W4 lang: `*lang` lookalike attributes are never read as `lang`', () => {
  // The same `(?<![\w-])` lookbehind the dir scan uses: `hreflang`,
  // `data-lang` and `xml:lang` must not answer this question.
  assert.equal(documentLanguage('<html data-lang="fr">'), null);
  assert.equal(documentLanguage('<html hreflang="fr">'), null);
  assert.equal(documentLanguage('<html xml:lang="fr">'), null);
  // …but a real one sitting beside a lookalike still wins.
  assert.equal(documentLanguage('<html data-lang="fr" lang="ja">'), 'ja');
});

test('W4 lang: resolveLanguage walks own → nearest ancestor → document', () => {
  const anc = (...langs) => langs.map((l) => ({ tag: 'div', attrs: l ? { lang: l } : {} }));
  // Own attribute wins outright.
  assert.equal(resolveLanguage({ lang: 'ja' }, anc('fr'), 'en'), 'ja');
  // Otherwise the NEAREST declaring ancestor — the chain is outermost-first.
  assert.equal(resolveLanguage({}, anc('en', 'fr'), 'de'), 'fr');
  assert.equal(resolveLanguage({}, anc('en', null), 'de'), 'en');
  // Nothing in the chain → the document element rung.
  assert.equal(resolveLanguage({}, anc(null, null), 'de'), 'de');
  // Nothing anywhere → null (absence means "unknown", never a default).
  assert.equal(resolveLanguage({}, anc(null), null), null);
  assert.equal(resolveLanguage(null, null, null), null);
});

test('W4 lang: HTML\'s explicit-unknown `lang=""` stops the walk without falling through', () => {
  // HTML §3.2.6.2 — an empty lang says "the language is not known", which is
  // the opposite of "ask my parent". Emitting the outer value here would
  // make the attribute a no-op.
  assert.equal(resolveLanguage({ lang: '' }, [{ tag: 'div', attrs: { lang: 'fr' } }], 'en'), null);
  assert.equal(resolveLanguage({}, [{ tag: 'div', attrs: { lang: '' } }], 'en'), null);
  assert.equal(documentLanguage('<html lang=""><body>'), '');
});

test('W4 lang: buildComponents stamps `_lang` on every element and on the body-root', () => {
  const html = '<body><p>outer <q lang="ja">inner</q></p></body>';
  const rules = parseCss('body { font: 32px serif }');
  // The <html lang> rung has to travel on the ctx — the walker's ancestor
  // chain starts INSIDE body.
  const { components } = buildComponents(html, rules, 'w4', { documentLang: 'fr' });
  assert.equal(components['w4__body']._lang, 'fr');
  assert.equal(components['w4__0']._lang, 'fr');
  assert.equal(components['w4__0'].children['w4__0__0']._lang, 'ja');
});

test('W4 lang: a lang-free document gains no `_lang` key at all', () => {
  // The byte-identity contract: every pre-wave-37 fixture without a lang
  // attribute must serialize exactly as it did before.
  const { components } = buildComponents(
    '<body><p>x</p></body>', parseCss('p { color: red }'), 'w4b',
  );
  for (const cmp of Object.values(components)) {
    assert.equal('_lang' in cmp, false);
  }
});

// ── wave-38 lane N4: @import resolution ──────────────────────────────────────
//
// The pins below hold the ONE-DIRECTIONAL gate the resolver is built around:
// an `@import` is inlined only when its condition is PROVEN to match, so a
// value we cannot evaluate behaves exactly like a value that evaluates false
// (both mean "leave the rule alone"). css-cascade/import-conditional-001 and
// -002 are the corpus tests that make this matter — each pairs a matching
// green sheet with a deliberately NON-matching red one, so a resolver that
// inlines everything paints the FAIL square.
import {
  mqLengthPx,
  mqFeature,
  mqQuery,
  mediaQueryListMatches,
  supportsConditionProvable,
  parseImportPrelude,
  importConditionMatches,
  resolveImports,
  MQ_VIEWPORT_WIDTH_PX,
  MQ_VIEWPORT_HEIGHT_PX,
} from './extract-fixture.mjs';
import {
  REF_RENDER_WIDTH as _REF_RENDER_WIDTH,
  REF_RENDER_MIN_HEIGHT as _REF_RENDER_MIN_HEIGHT,
} from './capture-browser-ref.mjs';

test('N4 @import: the media-query viewport IS the ref render viewport', () => {
  // extract-fixture.mjs cannot import capture-browser-ref.mjs (that module
  // imports extractRefHref from it — the cycle), so the two numbers are
  // duplicated. This is the pin that stops them drifting: a media query
  // evaluated against a viewport the reference never had would inline the
  // wrong arm of a `(min-width: …)` import.
  assert.equal(MQ_VIEWPORT_WIDTH_PX, _REF_RENDER_WIDTH);
  assert.equal(MQ_VIEWPORT_HEIGHT_PX, _REF_RENDER_MIN_HEIGHT);
});

test('N4 @import: mqLengthPx converts the absolute units and refuses the rest', () => {
  assert.equal(mqLengthPx('1px'), 1);
  assert.equal(mqLengthPx('40000in'), 3_840_000);   // import-conditional-001
  assert.equal(mqLengthPx('1in'), 96);
  assert.equal(mqLengthPx('72pt'), 96);
  assert.equal(mqLengthPx('1pc'), 16);
  assert.equal(mqLengthPx('2em'), 32);              // MQ-4 §1.3: INITIAL font size
  assert.equal(mqLengthPx('0'), 0);                 // unitless zero is a <length>
  assert.equal(mqLengthPx('5'), null);              // unitless non-zero is not
  assert.equal(mqLengthPx('50vw'), null);           // viewport units decline
  assert.equal(mqLengthPx('calc(1px + 1px)'), null);
});

test('N4 @import: mqFeature answers only the features it can measure', () => {
  assert.equal(mqFeature('(min-width: 1px)'), true);
  assert.equal(mqFeature(`(min-width: ${MQ_VIEWPORT_WIDTH_PX + 1}px)`), false);
  assert.equal(mqFeature(`(max-width: ${MQ_VIEWPORT_WIDTH_PX}px)`), true);  // inclusive
  assert.equal(mqFeature(`(width: ${MQ_VIEWPORT_WIDTH_PX}px)`), true);
  assert.equal(mqFeature('(orientation: portrait)'), true);                 // 358×568
  assert.equal(mqFeature('(orientation: landscape)'), false);
  // Unmodelled feature / unmodelled keyword → null, never a guess.
  assert.equal(mqFeature('(min-resolution: 2dppx)'), null);
  assert.equal(mqFeature('(orientation: sideways)'), null);
  assert.equal(mqFeature('(prefers-color-scheme: dark)'), null);
});

test('N4 @import: an unknown term poisons the whole query, it never defaults true', () => {
  assert.equal(mqQuery('screen and (min-width: 1px)'), true);
  // The unknown feature is ANDed with a true one — the answer must still be
  // "we do not know", not "the true half carried it".
  assert.equal(mqQuery('screen and (min-width: 1px) and (min-resolution: 2dppx)'), null);
  // …and with a FALSE one too: `not` would otherwise flip an unknown to true.
  assert.equal(mqQuery('(max-width: 1px) and (min-resolution: 2dppx)'), null);
  assert.equal(mqQuery('not (min-resolution: 2dppx)'), null);
});

test('N4 @import: media TYPES resolve against the screen-media ref', () => {
  assert.equal(mqQuery('all'), true);
  assert.equal(mqQuery('screen'), true);
  assert.equal(mqQuery('only screen'), true);        // `only` is a legacy no-op
  assert.equal(mqQuery('print'), false);
  assert.equal(mqQuery('not print'), true);
  // MQ-4 §2.1: an unknown media type never matches. `nonsense` is the token
  // import-conditional-001/002 use as their deliberate non-match.
  assert.equal(mqQuery('nonsense'), false);
});

test('N4 @import: a media LIST matches when any query does', () => {
  assert.equal(mediaQueryListMatches('(min-width: 1px) and (max-width: 40000in), nonsense'), true);
  assert.equal(mediaQueryListMatches('(max-width: 1px), nonsense'), false);
  assert.equal(mediaQueryListMatches('print, screen'), true);
});

test('N4 @import: supports() is answered from a closed table, never a heuristic', () => {
  assert.equal(supportsConditionProvable('(display: block)'), true);
  assert.equal(supportsConditionProvable('(display: grid)'), true);
  assert.equal(supportsConditionProvable('(--x: 1)'), true);      // custom props always
  // The import-conditional-002 trap: a property no engine has.
  assert.equal(supportsConditionProvable('(foo: bar)'), false);
  // A REAL property with a value the table does not vouch for still declines —
  // the table is an allow-list, not a property-name check.
  assert.equal(supportsConditionProvable('(display: ruby-text)'), false);
  assert.equal(supportsConditionProvable('(color: red)'), false);
  // Combinators and functional conditions are out of scope.
  assert.equal(supportsConditionProvable('(not (display: block))'), false);
  assert.equal(supportsConditionProvable('(display: block) and (color: red)'), false);
});

test('N4 @import: the prelude splits into href + condition tail', () => {
  assert.deepEqual(parseImportPrelude(' "support/test-green.css" supports(display: block)'),
    { href: 'support/test-green.css', rest: 'supports(display: block)' });
  assert.deepEqual(parseImportPrelude('url("./a.css")'), { href: './a.css', rest: '' });
  assert.deepEqual(parseImportPrelude("url(a.css) (min-width: 1px)"),
    { href: 'a.css', rest: '(min-width: 1px)' });
  // Not a form we can read off disk → href null, caller declines.
  assert.equal(parseImportPrelude('var(--sheet)').href, null);
});

test('N4 @import: the four corpus conditions land exactly as the tests intend', () => {
  // import-conditional-001: green imported, red NOT.
  assert.equal(importConditionMatches('(min-width: 1px) and (max-width: 40000in), nonsense'), true);
  assert.equal(importConditionMatches('(max-width: 1px), nonsense'), false);
  // import-conditional-002: same shape through supports().
  assert.equal(importConditionMatches('supports(display: block)'), true);
  assert.equal(importConditionMatches('supports(foo: bar)'), false);
  // Unconditional imports are trivially proven.
  assert.equal(importConditionMatches(''), true);
  // A cascade layer changes WHICH layer the rules land in — declined.
  assert.equal(importConditionMatches('layer(base)'), false);
  assert.equal(importConditionMatches('layer'), false);
  // supports() AND a media list: both halves must hold.
  assert.equal(importConditionMatches('supports(display: block) (min-width: 1px)'), true);
  assert.equal(importConditionMatches('supports(display: block) (max-width: 1px)'), false);
});

test('N4 @import: a matching import is inlined AT ITS PLACE, a non-matching one left verbatim', async () => {
  const os = await import('node:os');
  const path = await import('node:path');
  const { promises: fsp } = await import('node:fs');
  const dir = await fsp.mkdtemp(path.join(os.tmpdir(), 'n4-import-'));
  await fsp.writeFile(path.join(dir, 'green.css'), '.t { background: green }');
  await fsp.writeFile(path.join(dir, 'red.css'), '.t { background: red }');
  const sheet = '@import "red.css";\n@import "green.css" (min-width: 1px);\n'
    + '@import "red.css" (max-width: 1px);\ndiv { color: blue }';
  const r = await resolveImports(sheet, dir, dir);
  assert.equal(r.inlined, 2);
  assert.equal(r.declined, 1);
  // Order is the cascade: red's rules, then green's, then the leftover rule.
  assert.ok(r.css.indexOf('background: red }') < r.css.indexOf('background: green }'));
  // The declined rule survives BYTE-FOR-BYTE — parseCss skips it, which is
  // the pre-wave-38 behaviour for every @import.
  assert.ok(r.css.includes('@import "red.css" (max-width: 1px);'));
  await fsp.rm(dir, { recursive: true, force: true });
});

test('N4 @import: relative url() payloads are rebased onto the importing document', async () => {
  const os = await import('node:os');
  const path = await import('node:path');
  const { promises: fsp } = await import('node:fs');
  const dir = await fsp.mkdtemp(path.join(os.tmpdir(), 'n4-rebase-'));
  await fsp.mkdir(path.join(dir, 'support'));
  await fsp.writeFile(path.join(dir, 'support', 'face.css'),
    '@font-face { font-family: T; src: url(cap.ttf) }\n'
    + '.a { background: url("/images/x.png") }\n.b { background: url(data:image/png,%00) }');
  // The css-inline text-box-trim shape: the face's `src` is relative to the
  // SHEET, everything downstream resolves against the DOCUMENT.
  const r = await resolveImports('@import "support/face.css";', dir, dir);
  assert.equal(r.inlined, 1);
  assert.ok(r.css.includes('url("support/cap.ttf")'), r.css);
  // Server-root, data: and http(s) payloads resolve identically from either
  // base and must not be rewritten.
  assert.ok(r.css.includes('url("/images/x.png")'));
  assert.ok(r.css.includes('url(data:image/png,%00)'));
  await fsp.rm(dir, { recursive: true, force: true });
});

test('N4 @import: server-root hrefs are declined — the file:// ref cannot load them', async () => {
  const os = await import('node:os');
  const path = await import('node:path');
  const { promises: fsp } = await import('node:fs');
  const dir = await fsp.mkdtemp(path.join(os.tmpdir(), 'n4-root-'));
  // `@import "/fonts/ahem.css";` — 19 corpus tests, whose REFS import the
  // same unreachable sheet. Measured: delivering the face on the fixture side
  // alone moved 18 of the 19 SSIM DOWN.
  const r = await resolveImports('@import "/fonts/ahem.css";\ndiv{color:red}', dir, dir);
  assert.equal(r.inlined, 0);
  assert.equal(r.declined, 1);
  assert.ok(r.css.includes('@import "/fonts/ahem.css";'));
  await fsp.rm(dir, { recursive: true, force: true });
});

test('N4 @import: cycles, depth and misplaced rules all decline rather than loop', async () => {
  const os = await import('node:os');
  const path = await import('node:path');
  const { promises: fsp } = await import('node:fs');
  const dir = await fsp.mkdtemp(path.join(os.tmpdir(), 'n4-guard-'));
  // a.css imports b.css imports a.css.
  await fsp.writeFile(path.join(dir, 'a.css'), '@import "b.css";\n.a{color:red}');
  await fsp.writeFile(path.join(dir, 'b.css'), '@import "a.css";\n.b{color:blue}');
  const cyc = await resolveImports('@import "a.css";', dir, dir);
  assert.ok(cyc.css.includes('.a{color:red}'));
  assert.ok(cyc.css.includes('.b{color:blue}'));
  assert.ok(cyc.declined >= 1, 'the back-edge is declined, not followed');
  // css-syntax-3 §3.1: an @import after a style rule is invalid — a browser
  // drops it, so we must not honour it either.
  const late = await resolveImports('div{color:red}\n@import "a.css";', dir, dir);
  assert.equal(late.inlined, 0);
  assert.ok(late.css.includes('@import "a.css";'));
  // A sheet with no @import at all round-trips byte-identically.
  const none = await resolveImports('div{color:red}', dir, dir);
  assert.equal(none.css, 'div{color:red}');
  assert.equal(none.inlined, 0);
  await fsp.rm(dir, { recursive: true, force: true });
});

// ── wave-39 lane A5: THE dir-ATTRIBUTE BODY ROOT ────────────────────────────
//
// N6 (wave 38) wired the principal-direction propagation to the synthetic
// body-root; these pin the OTHER half — that a `<body dir=rtl>` /
// `<html dir=rtl>` document actually produces a body-root for it to ride,
// and that no LTR document moves.
import { mintDocumentDirection } from './extract-fixture.mjs';

test('A5 dir-mint: the §15.3.4 hint lands on the root bag, and only for rtl', () => {
  // The whole mapping: an rtl document direction becomes a real declaration.
  const bag = { props: {} };
  assert.equal(mintDocumentDirection(bag, 'rtl'), true);
  assert.deepEqual(bag.props, { direction: 'rtl' });
  // `ltr` is the INITIAL value — minting it would add a component and a baked
  // no-op to every LTR document for zero rendering change, so it is refused.
  const ltr = { props: {} };
  assert.equal(mintDocumentDirection(ltr, 'ltr'), false);
  assert.deepEqual(ltr.props, {});
  // No document-element dir attribute at all (documentDirectionality's null,
  // and the undefined a legacy direct caller leaves on the ctx).
  assert.equal(mintDocumentDirection({ props: {} }, null), false);
  assert.equal(mintDocumentDirection({ props: {} }, undefined), false);
  // Defensive shapes never throw.
  assert.equal(mintDocumentDirection(null, 'rtl'), false);
  assert.equal(mintDocumentDirection({}, 'rtl'), false);
});

test('A5 dir-mint: an author `direction` beats the UA-origin hint', () => {
  // CSS Cascade 5 §6.1 — a presentational hint is UA origin, so any
  // root-scope author declaration wins. Same guard the per-element dir path
  // uses (`('direction' in props) ? null : …`).
  const bag = { props: { direction: 'ltr' } };
  assert.equal(mintDocumentDirection(bag, 'rtl'), false);
  assert.equal(bag.props.direction, 'ltr');
});

test('A5 dir-mint: `<html dir=rtl>` with NO root-scope rule still emits a body-root', () => {
  // This is the deferral N6 recorded: before the mint, `matchedRules === 0`
  // meant no component at all, so the harness's ICB direction hoist had
  // nothing to read and the wave-30 A4 bake-down had no inheritance source.
  const html = '<html dir="rtl"><body><div>one</div></body></html>';
  const { components } = buildComponents(html, parseCss('div { color: lime }'), 'd1');
  assert.equal(components['d1__body'].properties.direction, 'rtl');
  assert.equal(components['d1__body']._role, 'body-root');
  // …and the inherited bake-down delivers it to the top-level child, which is
  // the body's SIBLING on the flat wire.
  assert.equal(components['d1__0'].properties.direction, 'rtl');
  assert.ok(components['d1__0']._lossyReasons.includes('body-inherited-baked'));
});

test('A5 dir-mint: `<body dir>` is the nearer rung, and ltr documents do not move', () => {
  // documentDirectionality answers body-first, so `<html dir=rtl><body dir=ltr>`
  // resolves to the INITIAL ltr — nothing is minted and no body-root appears
  // (the corpus-stillness guarantee for every LTR fixture).
  const mixed = buildComponents(
    '<html dir="rtl"><body dir="ltr"><div>one</div></body></html>',
    parseCss('div { color: lime }'), 'd2',
  ).components;
  assert.equal(mixed['d2__body'], undefined);
  assert.equal(mixed['d2__0'].properties.direction, undefined);
  // A plain `<html dir=ltr>` document is likewise untouched.
  const ltr = buildComponents(
    '<html dir="ltr"><body><div>one</div></body></html>',
    parseCss('div { color: lime }'), 'd3',
  ).components;
  assert.equal(ltr['d3__body'], undefined);
  // …and so is a document with no dir attribute anywhere.
  const none = buildComponents(
    '<html><body><div>one</div></body></html>',
    parseCss('div { color: lime }'), 'd4',
  ).components;
  assert.equal(none['d4__body'], undefined);
});

test('A5 dir-mint: the hint merges into an EXISTING root bag without displacing it', () => {
  // `<body dir=rtl>` alongside a real root-scope rule: the rule's own
  // declarations survive verbatim and the direction joins them, so the
  // canvas resolvers (background / writing-mode / direction) all read one bag.
  const html = '<html dir="rtl"><body><div>one</div></body></html>';
  const { components } = buildComponents(html, parseCss('body { background: green }'), 'd5');
  assert.equal(components['d5__body'].properties.background, 'green');
  assert.equal(components['d5__body'].properties.direction, 'rtl');
});

// ═════════════════════════════════════════════════════════════════════════════
// wave-44 lane U5 — (a) the `<ol reversed>` wire; (b) the real body-attr
// sentinel behind the post-load pseudo re-derivation.
// ═════════════════════════════════════════════════════════════════════════════

// New exports under test — dynamic import, same pattern as the wave-29 block.
const {
  LIST_ATTR_KEYS,
  LIST_BOOLEAN_ATTR_KEYS,
  widgetAttrsFor: u5WidgetAttrsFor,
  documentBodyAttrs,
} = await import('./extract-fixture.mjs');

// ── (a) `reversed` rides the list lane as a presence-boolean ────────────────

test('U5: LIST_ATTR_KEYS carries reversed, typed boolean by the lane subset', () => {
  // The allow-list order IS the wire key order (widgetAttrsFor walks it).
  assert.deepEqual(LIST_ATTR_KEYS, ['start', 'value', 'reversed']);
  // Exactly one boolean key today — HTML §4.4.5 pins `reversed` as boolean.
  assert.deepEqual([...LIST_BOOLEAN_ATTR_KEYS], ['reversed']);
});

test('U5: <ol reversed> forwards presence as literal true, whatever the source spelled', () => {
  // Bare attribute (walkChildren stores '' for it) — the common spelling.
  assert.deepEqual(u5WidgetAttrsFor('ol', { reversed: '' }), { reversed: true });
  // HTML boolean-attribute rule: ANY value means present — even "false"
  // (§2.3.2: "the values 'true' and 'false' are not allowed", presence wins).
  assert.deepEqual(u5WidgetAttrsFor('ol', { reversed: 'false' }), { reversed: true });
  // Alongside `start`: both lanes' typing rules hold in one bag —
  // counter-list-item's second reversed list is exactly <ol start="30" reversed>.
  assert.deepEqual(u5WidgetAttrsFor('ol', { start: '30', reversed: '' }),
    { start: '30', reversed: true });
  // Absent attribute ⇒ absent wire key (present-only contract, unchanged).
  assert.deepEqual(u5WidgetAttrsFor('ol', { start: '30' }), { start: '30' });
  // The widget lane did NOT gain the key: reversed on a widget tag is not a
  // widget attribute (the two lanes stay disjoint).
  assert.equal(u5WidgetAttrsFor('input', { reversed: '' }), null);
});

test('U5: buildComponents emits `_attrs` with reversed for <ol reversed>', () => {
  // End-to-end through the walker: the exact counter-list-item list shapes.
  const { components } = buildComponents(
    "<body><ol start='30' reversed><li>a</li></ol><ol><li>b</li></ol></body>",
    parseCss('ol { color: red }'), 'rv');
  assert.equal(components['rv__0']._tag, 'ol');
  assert.deepEqual(components['rv__0']._attrs, { start: '30', reversed: true });
  // The unreversed sibling stays attr-less — presence-only, no false noise.
  assert.equal(components['rv__1']._attrs, undefined);
});

// ── (b) documentBodyAttrs: the real <body …> bag ────────────────────────────

test('U5: documentBodyAttrs reads the real body start tag, omit-when-empty', () => {
  // The display-contents-dynamic-before-after-001 post-load shape: the
  // serialized DOM's body carries the script-set class.
  assert.deepEqual(documentBodyAttrs('<html><body class="active"><div></div></body></html>'),
    { class: 'active' });
  // Multiple attributes parse with the walker's own grammar (lowercased
  // keys, quoted or bare values).
  assert.deepEqual(documentBodyAttrs("<body ID='t' dir=rtl>x</body>"),
    { id: 't', dir: 'rtl' });
  // Attribute-less body → null, so the frozen empty sentinel stays in use.
  assert.equal(documentBodyAttrs('<body>x</body>'), null);
  // No body tag at all (fallback documents) → null.
  assert.equal(documentBodyAttrs('<div>no body</div>'), null);
  // Non-string input is refused, not thrown on.
  assert.equal(documentBodyAttrs(null), null);
});

test('U5: documentBodyAttrs scans MASKED markup — script strings and comments cannot fake a body', () => {
  // A `<body …>` inside a JS string (the cssom/computed-style-002 shape)
  // must not be read; the real body after it must be.
  assert.deepEqual(documentBodyAttrs(
    '<script>d.write(\'<body class="fake">\');</script><body class="real">x</body>'),
    { class: 'real' });
  // Same for a commented-out body.
  assert.deepEqual(documentBodyAttrs(
    '<!-- <body class="fake"> --><body class="real">x</body>'),
    { class: 'real' });
  // A quoted attr value containing `>` cannot truncate the tag slice (the
  // masked match spans to the REAL tag close, then slices original bytes).
  assert.deepEqual(documentBodyAttrs('<body class="a" title="x > y">t</body>'),
    { class: 'a', title: 'x > y' });
});

test('U5: ctx.bodyAttrs dresses the body sentinel — `.active div::before` flips buckets', () => {
  // The EXACT css-display/display-contents-dynamic-before-after-001 cascade:
  //   div::before         { color: red }
  //   .active div::before { color: green }
  // With `.active` on BODY, only a body sentinel wearing the class can let
  // the second rule match (the walker's chain starts inside body).
  const rules = parseCss('div::before { color: red } .active div::before { color: green }');
  const pos = { sibIndex: 0, sibCount: 1, sibTypeIndex: 0, sibTypeCount: 1, isEmpty: true };
  // Pre-mutation truth (no class on body): red, exactly as before wave-44.
  assert.equal(propsForElement(rules, 'div', {}, [], pos, {}).pseudo.before.color, 'red');
  // Post-mutation truth (serialized DOM's body carries class="active"): green.
  assert.equal(
    propsForElement(rules, 'div', {}, [], pos, { bodyAttrs: { class: 'active' } })
      .pseudo.before.color,
    'green');
});

test('U5: buildComponents harvests body attrs end-to-end into the pseudo bags', () => {
  // The re-derivation path in one call: extractFixture hands buildComponents
  // the SERIALIZED post-load document (body class present in markup), ctx
  // null — the bodyAttrs ctx rung must self-harvest, exactly like the dir
  // and lang rungs it mirrors.
  const css = 'div::before { content: ""; color: red } '
    + '.active div::before { content: ""; color: green }';
  const post = buildComponents(
    '<html><body class="active"><div>x</div></body></html>',
    parseCss(css), 'pb').components;
  assert.equal(post['pb__0']._pseudo.before.properties.color, 'green');
  // And the PRE-mutation source (no class in markup) still bakes red — the
  // static pass stays the honest pre-load truth.
  const pre = buildComponents(
    '<html><body><div>x</div></body></html>',
    parseCss(css), 'pr').components;
  assert.equal(pre['pr__0']._pseudo.before.properties.color, 'red');
});

test('U5: a caller-supplied ctx.bodyAttrs wins over harvesting (injection contract)', () => {
  // Same copy-not-mutate contract as documentDir/documentLang: a supplied
  // value (null included) is respected, the caller's object is untouched.
  const css = '.active div { color: green } div { color: red }';
  const ctx = { bodyAttrs: null };
  const out = buildComponents(
    '<html><body class="active"><div>x</div></body></html>',
    parseCss(css), 'ci', ctx).components;
  // Injection said "no body attrs", so `.active div` must NOT match even
  // though the markup carries the class.
  assert.equal(out['ci__0'].properties.color, 'red');
  // The caller's ctx object was not mutated by the rungs.
  assert.deepEqual(ctx, { bodyAttrs: null });
});
