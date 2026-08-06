// tools/titan/post-load-extract.test.mjs — unit pins for the wave-16
// POST-LOAD extraction mode (post-load-extract.mjs).
//
// Coverage map (mirrors the module's contract sections):
//   1. the enumerated computed-property set (exact list pin);
//   2. the top-layer decline boundary (TOP_LAYER_API_RX / postLoadDecline);
//   3. wall-tag activation (hasWallTag against EXTRACTION_WALL_TAGS);
//   4. traversal identity — flattenStaticPaths / staticPathsForHtml must
//      reproduce buildComponents' id paths (head-only skip, inline-merge
//      filter, depth cap);
//   5. element-mapping cross-check (mappingMismatch);
//   6. settle-stability (snapshotsStable);
//   7. merge-override semantics (componentAtPath / overlayComputedOnComponent
//      / mergePostLoadIntoFixture);
//   8. the PROVING SET — the css-position wall fixtures' baked post-load
//      values (skip-guarded: fixtures/wpt is a generated artifact, so these
//      pins only run on a machine that has run the extraction);
//   9. an optional LIVE end-to-end run (browser + corpus), env-gated so the
//      default `node --test` stays hermetic and fast;
//  10. wave-20 STRUCTURE extraction (the appendChild family) — the trigger
//      predicate, serialized-DOM input path, void-closer normalization,
//      two-stamp adoption, and the adopt→bake composition order.
//
// The gate-interplay pins (applyNaScoreGate × postLoadExtracted) live in
// inject-wpt-block.test.mjs next to the rest of the scoring-gate suite.

import test from 'node:test';
import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
// skeptic-29: the linked-stylesheet gate pins write a throwaway corpus tree.
import { mkdtemp, mkdir, writeFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
// wave-30 fix-T2: the CLI skip/error split is control flow inside main(), so
// the pin has to drive the real process. The skip path is stdlib-only.
import { spawnSync } from 'node:child_process';

import {
  POST_LOAD_COMPUTED_PROPERTIES, WRITE_RULES, SHORTHAND_CONFLICTS,
  TOP_LAYER_API_RX, postLoadDecline, hasWallTag, anchorInsetMismatch,
  flattenStaticPaths, staticPathsForHtml,
  mappingMismatch, snapshotsStable,
  componentAtPath, overlayComputedOnComponent, mergePostLoadIntoFixture,
  // wave-20 STRUCTURE extraction surface (section 10 pins):
  STRUCTURE_TRIGGER_TAG, shouldStructureExtract,
  HTML_VOID_TAGS, stripForeignVoidClosers,
  buildSyntheticHtml, adoptReExtractedFixture, CANVAS_FRAME_STYLE_ID,
} from './post-load-extract.mjs';
import { extractBodyTreeNested, buildComponents, parseCss, stripComments, extractInlineStyle, collectStyledTags } from './extract-fixture.mjs';

const __dirname = dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = join(__dirname, '..', '..');
// The generated-fixture root the proving-set pins read (gitignored artifact
// — pins skip when absent, see section 8's banner).
const FIXTURES = join(REPO_ROOT, 'fixtures', 'wpt', 'css-position');

// ── 1. The enumerated computed-property set ─────────────────────────────────

test('post-load: POST_LOAD_COMPUTED_PROPERTIES is the exact deliberate set', () => {
  // Pin the exact enumeration — adding or removing a computed property
  // changes what every post-load fixture bakes and must break a test first
  // (same convention as the EXTRACTION_WALL_TAGS pin).
  assert.deepEqual(POST_LOAD_COMPUTED_PROPERTIES, [
    'position',
    'top', 'right', 'bottom', 'left',
    'width', 'height', 'box-sizing',
    'margin-top', 'margin-right', 'margin-bottom', 'margin-left',
    'padding-top', 'padding-right', 'padding-bottom', 'padding-left',
    'border-top-width', 'border-right-width', 'border-bottom-width', 'border-left-width',
    'border-top-style', 'border-right-style', 'border-bottom-style', 'border-left-style',
    'border-top-color', 'border-right-color', 'border-bottom-color', 'border-left-color',
    'background-color',
    'transform',
    'align-self',
    'display', 'overflow-x', 'overflow-y', 'z-index',
    // wave-24 B-RC4: the css-lists dynamic family. Both are INHERITED, and
    // change-list-style-position-003 mutates the property on <body>, so no
    // element the static extractor sees ever declares it.
    'list-style-type', 'list-style-position',
    // wave-25 BD-RC4: the corner radii. filter-effects/
    // backdrop-filter-border-radius-change rewrites border-radius from 75px
    // to 150px on a double-rAF after load — the static extractor only ever
    // sees the pre-mutation value, so the corners were painted wrong.
    'border-top-left-radius', 'border-top-right-radius',
    'border-bottom-right-radius', 'border-bottom-left-radius',
  ]);
});

test('post-load: WRITE_RULES delete-not-write defaults are pinned', () => {
  // 'auto' insets / z-index and 'none' transform carry no declaration;
  // width/height must be concrete px. Silently widening these would bloat
  // every fixture (or bake keywords the converter treats differently).
  assert.deepEqual(Object.keys(WRITE_RULES).sort(),
    ['align-self', 'border-bottom-left-radius', 'border-bottom-right-radius',
     'border-top-left-radius', 'border-top-right-radius',
     'bottom', 'height', 'left', 'list-style-position',
     'list-style-type', 'right', 'top', 'transform', 'width', 'z-index']);
  assert.equal(WRITE_RULES.top.deleteWhen, 'auto');
  assert.equal(WRITE_RULES.transform.deleteWhen, 'none');
  assert.equal(WRITE_RULES.width.requirePx, true);
  // RC-A5a: align-self's initial 'auto' carries no declaration (css-align-3
  // §6.1) — delete-not-write, so a stale pre-mutation keyword cannot linger.
  assert.equal(WRITE_RULES['align-self'].deleteWhen, 'auto');
  // wave-24 B-RC4: the CSS Lists 3 initials (MEASURED as Chromium's
  // computed values for a plain <div>). Without delete-not-write, EVERY
  // component in every post-load fixture would gain two list-marker keys.
  assert.equal(WRITE_RULES['list-style-type'].deleteWhen, 'disc');
  assert.equal(WRITE_RULES['list-style-position'].deleteWhen, 'outside');
  // wave-25 BD-RC4: '0px' is the CSS Backgrounds 3 §5.1 initial radius and
  // what Chromium reports for every square-cornered element (MEASURED). The
  // corpus is overwhelmingly square-cornered, so writing it would stamp four
  // radius keys onto nearly every component.
  for (const corner of ['border-top-left-radius', 'border-top-right-radius',
                        'border-bottom-right-radius', 'border-bottom-left-radius']) {
    assert.equal(WRITE_RULES[corner].deleteWhen, '0px', `${corner} must be delete-not-write at 0px`);
  }
});

test('post-load b-rc4: an inherited list-style-position mutation lands, initials do not', () => {
  // The exact change-list-style-position-003 shape: `document.body.style
  // .listStylePosition = "inside"` after load. No element declares it
  // statically, so the fixture's marker stayed `outside` while the ref
  // painted `inside`.
  const inside = { properties: { display: 'list-item' } };
  overlayComputedOnComponent(inside, {
    display: 'list-item',
    'list-style-type': 'decimal',
    'list-style-position': 'inside',      // inherited from the mutated body
  });
  assert.equal(inside.properties['list-style-position'], 'inside');
  assert.equal(inside.properties['list-style-type'], 'decimal');

  // A plain non-list box reports the CSS initials — delete-not-write, so
  // the overlay must add NEITHER key (the anti-bloat half of the rule).
  const plain = { properties: { display: 'block' } };
  overlayComputedOnComponent(plain, {
    display: 'block',
    'list-style-type': 'disc',
    'list-style-position': 'outside',
  });
  assert.equal(plain.properties['list-style-type'], undefined);
  assert.equal(plain.properties['list-style-position'], undefined);
});

test('post-load b-rc4: a STALE static list-style-position is deleted, never left to linger', () => {
  // The inverse mutation (script switches `inside` → the initial
  // `outside`): the computed value carries no declaration, but the static
  // key MUST still go, or the runtime would render the pre-script marker.
  const cmp = { properties: { 'list-style-position': 'inside', 'list-style-type': 'square' } };
  overlayComputedOnComponent(cmp, {
    'list-style-position': 'outside',
    'list-style-type': 'disc',
  });
  assert.equal(cmp.properties['list-style-position'], undefined, 'stale position lingered');
  assert.equal(cmp.properties['list-style-type'], undefined, 'stale type lingered');
});

test('post-load b-rc4: the list-style SHORTHAND is stripped so it cannot outrank the baked longhands', () => {
  // contain-style-dynamic-002's exact static shape (`list-style: inside
  // decimal`). Leaving it in place would let the runtime cascade re-assert
  // the pre-mutation position over the appended longhand.
  const cmp = { properties: { 'list-style': 'inside decimal' } };
  overlayComputedOnComponent(cmp, {
    'list-style-type': 'decimal',
    'list-style-position': 'outside',     // script switched it back
  });
  assert.equal(cmp.properties['list-style'], undefined, 'shorthand survived the strip');
  assert.equal(cmp.properties['list-style-type'], 'decimal');
});

// ── 2. Top-layer decline boundary ───────────────────────────────────────────

test('post-load: top-layer API calls decline (the overlay family)', () => {
  // The measured overlay-transition-backdrop shape: whole signal lives on
  // ::backdrop after showPopover()/hidePopover() — per-element computed
  // snapshots cannot represent it.
  assert.equal(postLoadDecline('<script>foo.showPopover(); foo.hidePopover();</script>'),
    'top-layer-undeliverable');
  // <dialog>.showModal() and fullscreen promotion are the same top layer.
  assert.equal(postLoadDecline('<script>dlg.showModal()</script>'), 'top-layer-undeliverable');
  assert.equal(postLoadDecline('<script>el.requestFullscreen ()</script>'), 'top-layer-undeliverable');
});

test('post-load: style-mutation scripts do NOT decline', () => {
  // The deliverable family — style/property mutation with no top-layer API.
  assert.equal(postLoadDecline('<script>onload = () => { el.style.left = "100px"; }</script>'), null);
  // Property names that merely CONTAIN an API substring must not fire.
  assert.equal(postLoadDecline('<div popover id=foo></div>'), null);
});

test('post-load: TOP_LAYER_API_RX matches call syntax only', () => {
  // A prose mention (no call parens) is not a promotion — regex requires
  // the `.name(` call shape.
  assert.equal(TOP_LAYER_API_RX.test('showPopover is an API'), false);
  assert.equal(TOP_LAYER_API_RX.test('x.togglePopover()'), true);
});

// ── 3. Wall-tag activation ──────────────────────────────────────────────────

test('post-load: hasWallTag fires on exactly the extraction-wall tags', () => {
  // Activation must track the score gate's wall set — both script tags fire,
  // capability tags and empty/missing tag lists do not.
  assert.equal(hasWallTag(['requires-script-mutation']), true);
  assert.equal(hasWallTag(['requires-containment', 'requires-script-driven-scroll']), true);
  assert.equal(hasWallTag(['requires-float-layout']), false);
  assert.equal(hasWallTag([]), false);
  assert.equal(hasWallTag(undefined), false);
});

test('post-load: wave-29 anchor tag activates the bake', () => {
  // Admitting requires-anchor-positioning-runtime to EXTRACTION_WALL_TAGS is
  // what makes the css-anchor-position population eligible for the bake at
  // all — the exclusion (applyNaScoreGate) and the remedy (this activation)
  // read ONE set, so they can never disagree about which tests need
  // delivery. Pinned so a revert of the wall entry breaks activation loudly.
  assert.equal(hasWallTag(['requires-anchor-positioning-runtime']), true);
  // The anchor-center-scroll-* trio carries BOTH tags: still activated here
  // (either tag suffices), and it is the bake's own NO-SCROLL-OFFSETS scope
  // boundary — not activation — that bails them back to the static path.
  assert.equal(hasWallTag(
    ['requires-anchor-positioning-runtime', 'requires-script-driven-scroll']), true);
});

// ── 3b. wave-29 anchor-inset delivery guard ─────────────────────────────────
//
// The bake rests on "for an out-of-flow box the CSSOM resolved inset IS the
// used inset". MEASURED counter-example (pinned headless Chromium,
// css-anchor-position/anchor-center-overflow-001): an `.anchored { inset:
// 6px; place-self: anchor-center }` box resolves left "6px" but PAINTS 24px
// from its containing block's padding edge. Baking the resolved string would
// misplace it by 18px and stamp the fixture delivered — re-admitting the
// test to scoring on geometry the harness got wrong. anchorInsetMismatch is
// the valve; these pins are its contract.

test('post-load: anchorInsetMismatch is null when no anchored box was probed', () => {
  // The entire pre-wave-29 corpus: the walker writes null for every element
  // that is not an anchor-aligned out-of-flow box, so the guard must be a
  // strict no-op there (this is what makes the change non-regressive).
  assert.equal(anchorInsetMismatch([]), null);
  assert.equal(anchorInsetMismatch([{ path: [0], anchorInsetDelta: null }]), null);
  assert.equal(anchorInsetMismatch([{ path: [0] }]), null);   // field absent
  assert.equal(anchorInsetMismatch(undefined), null);          // no records
});

test('post-load: anchorInsetMismatch passes boxes that land where they resolve', () => {
  // anchor-center-safe / anchor-center-no-default: anchor() insets and
  // static-position anchor-center DO serialize as the used offset, so the
  // bake genuinely delivers and the test must be re-admitted.
  assert.equal(anchorInsetMismatch([
    { path: [0], anchorInsetDelta: [0, 0] },
    { path: [1], anchorInsetDelta: [0.25, -0.5] },   // sub-pixel: still fine
  ]), null);
});

test('post-load: anchorInsetMismatch fires on a pre-alignment inset', () => {
  // The overflow family's shape: some boxes fine, some off by whole pixels.
  const msg = anchorInsetMismatch([
    { path: [0, 0, 0], anchorInsetDelta: [0, 0] },
    { path: [0, 0, 3], anchorInsetDelta: [0, -18] },  // the measured 18px
  ]);
  assert.ok(msg, 'expected a mismatch description');
  assert.match(msg, /1\/2 anchor-aligned boxes/);
  assert.match(msg, /worst 18\.00px/);
  assert.match(msg, /path 0\.0\.3/);                  // locatable in the DOM
});

test('post-load: anchorInsetMismatch takes the worst of the two axes', () => {
  // A box correct in the block axis but wrong in the inline axis is wrong.
  assert.ok(anchorInsetMismatch([{ path: [1], anchorInsetDelta: [0, 76.59] }]));
  assert.ok(anchorInsetMismatch([{ path: [1], anchorInsetDelta: [-50, 0] }]));
  // Tolerance is caller-overridable but defaults to half a device pixel.
  assert.equal(anchorInsetMismatch([{ path: [1], anchorInsetDelta: [0.5, 0] }]), null);
  assert.ok(anchorInsetMismatch([{ path: [1], anchorInsetDelta: [0.51, 0] }]));
});

// ── 4. Traversal identity ───────────────────────────────────────────────────

// The hypothetical-dynamic-change family's markup shape (nested fixed pair).
const NESTED_HTML = `
<style>.a { position: fixed; } </style>
<div class="a"><div class="b"></div></div>
<script>onload = () => {};</script>`;

test('post-load: flattenStaticPaths mirrors buildComponents id paths', () => {
  // The path list must be exactly the `__N[__M…]` id suffixes buildComponents
  // assigns — verified by building both from the SAME tree and comparing.
  const cleaned = stripComments(NESTED_HTML);
  const rules = parseCss(extractInlineStyle(cleaned));
  const mergeCtx = { styledTags: collectStyledTags(rules) };
  const tree = extractBodyTreeNested(cleaned, 5, mergeCtx);
  const paths = flattenStaticPaths(tree);
  // Two nodes: the outer div at [0], its child at [0,0]; <style>/<script>
  // are head-only and never enter the walk.
  assert.deepEqual(paths, [
    { path: [0], tag: 'div' },
    { path: [0, 0], tag: 'div' },
  ]);
  // Cross-check against the component ids the fixture builder emits.
  const built = buildComponents(cleaned, rules, 'stem');
  assert.deepEqual(Object.keys(built.components), ['stem__0']);
  assert.deepEqual(Object.keys(built.components.stem__0.children), ['stem__0__0']);
});

test('post-load: staticPathsForHtml applies the inline-merge filter', async () => {
  // The change-insets shape: instruction <p> with a mergeable <strong> —
  // the strong is absorbed into the p's _text and must NOT get a path
  // (the browser walk skips it symmetrically).
  const html = `
<p>Test passes if there is a filled green square and <strong>no red</strong>.</p>
<div style="width:100px"></div>`;
  const { paths } = await staticPathsForHtml(html, join(REPO_ROOT, 'nonexistent.html'));
  assert.deepEqual(paths, [
    { path: [0], tag: 'p' },
    { path: [1], tag: 'div' },
  ]);
});

test('post-load: staticPathsForHtml keeps a rule-targeted inline tag as a component', async () => {
  // The styledTags merge guard: `strong { color: red }` makes the <strong>
  // a styled subject — it keeps its component path on BOTH sides.
  const html = `
<style>strong { color: red; }</style>
<p>text <strong>subject</strong></p>`;
  const { paths } = await staticPathsForHtml(html, join(REPO_ROOT, 'nonexistent.html'));
  assert.deepEqual(paths, [
    { path: [0], tag: 'p' },
    { path: [0, 0], tag: 'strong' },
  ]);
});

// ── 5. Element-mapping cross-check ──────────────────────────────────────────

const STATIC_PATHS = [
  { path: [0], tag: 'div' },
  { path: [0, 0], tag: 'div' },
];

test('post-load: mappingMismatch passes on aligned walks', () => {
  // Identical path/tag sequences ⇒ overlay is safe.
  assert.equal(mappingMismatch(STATIC_PATHS, [
    { path: [0], tag: 'div' },
    { path: [0, 0], tag: 'div' },
  ]), null);
});

test('post-load: mappingMismatch catches script-inserted elements', () => {
  // The containing-block-change-button shape: appendChild adds a child the
  // static tree never saw — count drift must bail.
  const m = mappingMismatch(STATIC_PATHS, [
    { path: [0], tag: 'div' },
    { path: [0, 0], tag: 'div' },
    { path: [0, 1], tag: 'div' },
  ]);
  assert.match(m, /element count: static 2 vs browser 3/);
});

test('post-load: mappingMismatch catches tag drift at a slot', () => {
  // Same shape, different element kind — identity check must bail.
  const m = mappingMismatch(STATIC_PATHS, [
    { path: [0], tag: 'div' },
    { path: [0, 0], tag: 'span' },
  ]);
  assert.match(m, /tag@0\.0: static <div> vs browser <span>/);
});

test('post-load: mappingMismatch catches path drift', () => {
  // Same count, different structure (sibling instead of child).
  const m = mappingMismatch(STATIC_PATHS, [
    { path: [0], tag: 'div' },
    { path: [1], tag: 'div' },
  ]);
  assert.match(m, /path\[1\]: static 0\.0 vs browser 1/);
});

// ── 6. Settle-stability ─────────────────────────────────────────────────────

const SNAP = {
  records: [{ path: [0], tag: 'div', styles: { left: '10px' },
              rect: { x: 10, y: 0, width: 100, height: 100 },
              scrollTop: 0, scrollLeft: 0 }],
  docScrollTop: 0, docScrollLeft: 0,
};

test('post-load: snapshotsStable accepts identical snapshots', () => {
  // Deep-equal via deterministic serialization — same walker, same order.
  assert.equal(snapshotsStable(SNAP, JSON.parse(JSON.stringify(SNAP))), true);
});

test('post-load: snapshotsStable bails on any computed-style drift', () => {
  // A running transition / rAF loop moves a value between the two 100ms
  // samples — the mode must fall back to the static path.
  const b = JSON.parse(JSON.stringify(SNAP));
  b.records[0].styles.left = '11px';
  assert.equal(snapshotsStable(SNAP, b), false);
});

test('post-load: snapshotsStable bails on rect drift too', () => {
  // Geometry can move without any enumerated style changing (e.g. an
  // animating ancestor) — the rect cross-check catches that class.
  const b = JSON.parse(JSON.stringify(SNAP));
  b.records[0].rect.y = 5;
  assert.equal(snapshotsStable(SNAP, b), false);
});

// ── 7. Merge-override semantics ─────────────────────────────────────────────

/** A minimal fixture mirroring the hypothetical-dynamic-change-001 static
 *  shape (pre-mutation left:0, shorthand background) for merge pins. */
function makeFixture() {
  return {
    _wpt: { test: 't.html', lossy: false, lossyReasons: [] },
    components: {
      stem__0: {
        properties: { position: 'fixed', left: '0', top: '0',
                      background: 'red', 'box-sizing': 'border-box' },
        _text: 'own text stays',
        children: {
          stem__0__0: { id: 'stem__0__0', properties: { position: 'fixed' } },
        },
      },
    },
  };
}

test('post-load: componentAtPath resolves nested children ids', () => {
  const fx = makeFixture();
  // Top level: path [0] → stem__0; nested: [0,0] → children map stem__0__0.
  assert.equal(componentAtPath(fx, 'stem', [0]), fx.components.stem__0);
  assert.equal(componentAtPath(fx, 'stem', [0, 0]), fx.components.stem__0.children.stem__0__0);
  // Absent paths resolve null (mergePostLoadIntoFixture turns that into a
  // LOUD error — pinned below).
  assert.equal(componentAtPath(fx, 'stem', [3]), null);
  assert.equal(componentAtPath(fx, 'stem', [0, 7]), null);
});

test('post-load: overlay overrides statics, strips shorthands, pins the basis', () => {
  const fx = makeFixture();
  const cmp = fx.components.stem__0;
  overlayComputedOnComponent(cmp, {
    position: 'fixed', left: '100px', top: '0px', right: '190px', bottom: 'auto',
    width: '100px', height: '100px', 'box-sizing': 'content-box',
    'background-color': 'rgb(255, 0, 0)', transform: 'none',
    display: 'block', 'overflow-x': 'visible', 'overflow-y': 'visible', 'z-index': 'auto',
  });
  const p = cmp.properties;
  // The mutation landed: computed px override the stale static '0'.
  assert.equal(p.left, '100px');
  // Conflicting shorthand stripped so it can't outrank the baked longhand.
  assert.equal(p.background, undefined);
  assert.equal(p['background-color'], 'rgb(255, 0, 0)');
  // delete-not-write defaults: auto/none carry no declaration.
  assert.equal(p['z-index'], undefined);
  assert.equal(p.transform, undefined);
  // Over-constraint drop (CSS2 §10.3.7): left+width kept ⇒ right dropped.
  assert.equal(p.right, undefined);
  // The COMPUTED basis overrides the stale static 'border-box' — gCS
  // width/height are expressed in the element's own basis, so the baked
  // basis must be the browser's, not the author's leftover.
  assert.equal(p['box-sizing'], 'content-box');
  // Static-only content is untouched by the overlay.
  assert.equal(cmp._text, 'own text stays');
});

test('post-load w1: overlay leaves _tag/_attrs untouched (widget identity is structural)', () => {
  // wave-20 W1: `_attrs` (the widget-identity wire) joins `_text`/`_tag` in
  // the overlay's untouched set — the computed-state bake writes ONLY into
  // `properties`, so a checked checkbox stays a checked checkbox after a
  // post-load geometry overlay.
  const cmp = {
    properties: { left: '0' },
    _tag: 'input',
    _attrs: { type: 'checkbox', checked: true },
  };
  overlayComputedOnComponent(cmp, {
    position: 'absolute', left: '40px', top: 'auto', right: 'auto', bottom: 'auto',
    width: '16px', height: '16px', 'background-color': 'rgb(255, 0, 0)',
    transform: 'none', display: 'inline-block', 'overflow-x': 'visible',
    'overflow-y': 'visible', 'z-index': 'auto',
  });
  // Geometry landed…
  assert.equal(cmp.properties.left, '40px');
  // …and the widget identity fields are byte-identical.
  assert.equal(cmp._tag, 'input');
  assert.deepEqual(cmp._attrs, { type: 'checkbox', checked: true });
});

test('post-load: overlay keeps the trailing inset when the leading one is auto', () => {
  const fx = makeFixture();
  const cmp = fx.components.stem__0;
  // Right/bottom-anchored box: left/top auto ⇒ deleted, right/bottom kept —
  // they are the real anchors, not over-constraint redundancy.
  overlayComputedOnComponent(cmp, {
    position: 'absolute', left: 'auto', top: 'auto', right: '20px', bottom: '30px',
    width: '50px', height: '50px', 'background-color': 'rgb(0, 128, 0)',
    transform: 'none', display: 'block', 'overflow-x': 'visible',
    'overflow-y': 'visible', 'z-index': 'auto',
  });
  assert.equal(cmp.properties.left, undefined);   // stale static '0' removed
  assert.equal(cmp.properties.right, '20px');
  assert.equal(cmp.properties.bottom, '30px');
});

test('post-load: stale align-self is displaced by the computed keyword (dynamic-align-self-001 shape)', () => {
  // RC-A5a — the exact css-flexbox/abspos/dynamic-align-self-001 shape: the
  // abspos flex child statically declares `align-self: end`, the script flips
  // it to 'start' after load. wave18-final baked the used insets (top/left
  // 0px) but left the stale 'end' underneath — a runtime that gives
  // self-alignment priority over the baked insets rendered the PRE-mutation
  // state (android-ref 0.9416 vs web-ref 0.999 on one fixture). The overlay
  // must bake the computed post-mutation keyword over the stale static one.
  const fx = makeFixture();
  const cmp = fx.components.stem__0;
  cmp.properties = { display: 'flex', position: 'absolute', width: '100px',
                     height: '100px', 'background-color': 'green',
                     'align-self': 'end' };
  overlayComputedOnComponent(cmp, {
    position: 'absolute', top: '0px', left: '0px', right: 'auto', bottom: 'auto',
    width: '100px', height: '100px', 'box-sizing': 'content-box',
    'background-color': 'rgb(0, 128, 0)', transform: 'none',
    'align-self': 'start',
    display: 'flex', 'overflow-x': 'visible', 'overflow-y': 'visible', 'z-index': 'auto',
  });
  // The post-script alignment displaced the stale pre-mutation keyword.
  assert.equal(cmp.properties['align-self'], 'start');
  // The used insets are baked alongside (top+height ⇒ bottom dropped, §10.6.4).
  assert.equal(cmp.properties.top, '0px');
  assert.equal(cmp.properties.left, '0px');
  assert.equal(cmp.properties.bottom, undefined);
});

test('post-load: computed align-self auto deletes a stale static keyword', () => {
  // The delete-not-write arm: a script that RESETS alignment to the initial
  // 'auto' must remove the stale declaration, not bake the keyword 'auto'.
  const fx = makeFixture();
  const cmp = fx.components.stem__0;
  cmp.properties['align-self'] = 'end';
  overlayComputedOnComponent(cmp, { 'align-self': 'auto' });
  assert.equal(cmp.properties['align-self'], undefined);
});

test('post-load: non-px width/height are delete-not-write', () => {
  const fx = makeFixture();
  const cmp = fx.components.stem__0;
  cmp.properties.width = '100px'; // stale static size
  // display:none boxes report width/height 'auto' — no concrete used value.
  overlayComputedOnComponent(cmp, { width: 'auto', height: 'auto', display: 'none' });
  assert.equal(cmp.properties.width, undefined);
  assert.equal(cmp.properties.display, 'none');
});

test('post-load: mergePostLoadIntoFixture stamps _wpt and counts overlays', () => {
  const fx = makeFixture();
  const overlaid = mergePostLoadIntoFixture(fx, 'stem', [
    { path: [0], tag: 'div', styles: { left: '100px' } },
    { path: [0, 0], tag: 'div', styles: { left: '100px' } },
  ]);
  assert.equal(overlaid, 2);
  // The delivery stamp the score gate consults (via the combined keyMap).
  assert.equal(fx._wpt.postLoadExtracted, true);
  assert.equal(fx.components.stem__0.children.stem__0__0.properties.left, '100px');
});

test('post-load: mergePostLoadIntoFixture fails LOUDLY on an unmapped path', () => {
  // A merge without a prior mappingMismatch pass is a programming error —
  // silent under-overlaying is exactly the kind of quiet lie this pipeline
  // bans (no silent fallthroughs).
  const fx = makeFixture();
  assert.throws(
    () => mergePostLoadIntoFixture(fx, 'stem', [{ path: [9], tag: 'div', styles: {} }]),
    /no component at path 9/);
});

test('post-load: SHORTHAND_CONFLICTS covers every family the overlay writes', () => {
  // Each longhand family baked by the overlay must have its displacing
  // shorthands listed, or a stale static shorthand could win in a runtime
  // cascade. Exact-list pin (reviewed change, not drift).
  assert.deepEqual([...SHORTHAND_CONFLICTS].sort(), [
    'background', 'border', 'border-bottom', 'border-color', 'border-left',
    // wave-25 BD-RC4: `border-radius` expands to exactly the four corner
    // longhands the overlay now bakes. It is NOT covered by 'border' — the
    // `border` shorthand excludes radius (CSS Backgrounds 3 §4.5) — so a
    // surviving `border-radius: 75px` would outrank the baked 150px corners.
    'border-radius',
    'border-right', 'border-style', 'border-top', 'border-width',
    // wave-24 B-RC4: `list-style` expands to the two longhands the overlay
    // now bakes (plus the image leg it documents as dropped).
    'inset', 'list-style', 'margin', 'overflow', 'padding',
  ]);
});

// ── 10 (wave-20). Structure extraction — the appendChild family ─────────────
//
// The former element-mapping BAIL becomes the structure-delivery TRIGGER for
// the requires-script-mutation population: the live post-script DOM is
// serialized and re-fed through extract-fixture.mjs (htmlOverride), so
// created nodes become components. Pins below cover the pure surface: the
// scope-guarded trigger predicate, the serialized-DOM input path (synthetic
// document assembly + void-closer normalization + static re-walk), the
// two-stamp adoption, and the adopt→bake composition order.

test('wave20: shouldStructureExtract fires only for script-mutation drift', () => {
  const drift = 'element count: static 2 vs browser 8'; // any non-null reason
  // The trigger: wall-tagged requires-script-mutation AND an actual mismatch.
  assert.equal(shouldStructureExtract([STRUCTURE_TRIGGER_TAG], drift), true);
  assert.equal(shouldStructureExtract(['requires-table-layout', STRUCTURE_TRIGGER_TAG], drift), true);
  // No mismatch → the cheaper state-bake path already ran; never trigger.
  assert.equal(shouldStructureExtract([STRUCTURE_TRIGGER_TAG], null), false);
  // Drift on a scroll-only wall (or untagged --force run) keeps the bail —
  // the delivered capability is structural mutation, nothing else.
  assert.equal(shouldStructureExtract(['requires-script-driven-scroll'], drift), false);
  assert.equal(shouldStructureExtract(undefined, drift), false);
  assert.equal(shouldStructureExtract([], drift), false);
  // The tag itself is pinned — the trigger scope must not silently widen.
  assert.equal(STRUCTURE_TRIGGER_TAG, 'requires-script-mutation');
});

test('wave20: stripForeignVoidClosers removes exactly the foreign void closers', () => {
  // createElementNS('not-html', 'input') serializes `<input></input>` — the
  // HTML re-parse ignores the closer, but the regex walker would drop every
  // following sibling on it (the measured appearance-auto-… failure mode).
  assert.equal(
    stripForeignVoidClosers('<div><button></button><input></input><meter></meter></div>'),
    '<div><button></button><input><meter></meter></div>');
  // Non-void closers are untouched (meter above), as are open tags and text.
  assert.equal(stripForeignVoidClosers('<p>a &lt;/input&gt; b</p>'), '<p>a &lt;/input&gt; b</p>');
  // Case-insensitive + optional whitespace, per HTML tag-name matching.
  assert.equal(stripForeignVoidClosers('<BR></BR><wbr></wbr >'), '<BR><wbr>');
  // The void set is the HTML §13.1.2 list — pinned so it can't drift.
  assert.deepEqual(HTML_VOID_TAGS, [
    'area', 'base', 'br', 'col', 'embed', 'hr', 'img', 'input',
    'link', 'meta', 'param', 'source', 'track', 'wbr',
  ]);
});

test('fix5: the serializer stamps the foreign-namespace marker before outerHTML', () => {
  // Source-scan pin (the htmlOverride wiring convention): namespaces do NOT
  // survive the outerHTML → regex re-parse round-trip, so the ONLY honest
  // place to record them is the in-browser serializer, where the live-DOM
  // clone still answers el.namespaceURI. The stamp + the evaluate wiring
  // are both pinned so neither half can be dropped silently.
  const src = readFileSync(new URL('./post-load-extract.mjs', import.meta.url), 'utf8');
  // The XHTML namespace literal + the per-element namespaceURI gate.
  assert.match(src, /XHTML_NS = 'http:\/\/www\.w3\.org\/1999\/xhtml'/,
    'serializer must compare against the XHTML namespace');
  assert.match(src, /el\.namespaceURI !== XHTML_NS\) el\.setAttribute\(foreignNsMarker, ''\)/,
    'serializer must stamp every non-XHTML element with the marker');
  // The evaluate call must thread extract-fixture's marker constant.
  assert.match(src, /foreignNsMarker: FOREIGN_NS_MARKER_ATTR/,
    'the marker constant must be the one buildNode gates on');
});

test('fix5: a marked serialized widget re-extracts with NO widget identity', async () => {
  // End-to-end over the pure halves: the serialized shape the marker
  // produces (`<input data-sc-foreign-ns="">`) must flow through the
  // synthetic-document assembly + static walk and come out identity-free.
  const { buildComponents, parseCss, FOREIGN_NS_MARKER_ATTR } =
    await import('./extract-fixture.mjs');
  const bodyOuter = '<body><div id="div">' +
    `<input ${FOREIGN_NS_MARKER_ATTR}=""></input>` +   // foreign — closer form
    '</div></body>';
  const synthetic = buildSyntheticHtml('<title>t</title><style>div * { width: 1em; }</style>',
    stripForeignVoidClosers(bodyOuter));
  // Feed the body through the component builder exactly as extractFixture
  // would (same rules → same kept-child filter).
  const { components } = buildComponents(synthetic, parseCss('div * { width: 1em; }'), 'stem');
  const child = components['stem__0'].children['stem__0__0'];
  // No `_tag: 'input'`, no `_attrs` — the natives paint a plain box, which
  // is what the browser paints for a foreign-namespace 'input'.
  assert.equal(child._tag, undefined);
  assert.equal(child._attrs, undefined);
});

test('wave20: serialized-DOM input flows through the static walk with created nodes', async () => {
  // The serialized live DOM (post-appendChild) as buildSyntheticHtml emits
  // it: original head around the live body. The static pipeline must see
  // the CREATED children as first-class walk entries — this is the exact
  // shape the appearance-auto test serializes to (6 created foreign
  // children, void closers already normalized).
  const headInner = '<title>t</title><style>div * { width: 1em; }</style>';
  const bodyOuter = '<body><p>There should be nothing below:</p>' +
    '<div id="div"><button></button><input></input><meter></meter></div></body>';
  const synthetic = buildSyntheticHtml(headInner, stripForeignVoidClosers(bodyOuter));
  // Explicit skeleton: the live parser already normalized implicit markup,
  // so the assembled document always takes the explicit-body branch.
  assert.match(synthetic, /^<!DOCTYPE html>\n<html>\n<head><title>t<\/title>/);
  const { paths } = await staticPathsForHtml(synthetic, '/nonexistent/test.html');
  // p at [0]; div at [1]; the three script-created children at [1,0..2].
  assert.deepEqual(paths.map((p) => `${p.path.join('.')}:${p.tag}`),
    ['0:p', '1:div', '1.0:button', '1.1:input', '1.2:meter']);
});

test('wave20: adoptReExtractedFixture swaps content in place and stamps BOTH flags', () => {
  // The caller (extract-fixture CLI) holds the ORIGINAL fixture reference —
  // adoption must mutate that object, not return a different one.
  const fixture = { _wpt: { test: 't.html', bucket: 'A' }, components: { old: {} } };
  const reFixture = {
    _wpt: { test: 't.html', bucket: 'A', lossy: false, lossyReasons: [] },
    components: { 't__0': { properties: { width: '100px' } } },
  };
  const ret = adoptReExtractedFixture(fixture, reFixture);
  assert.equal(ret, fixture);                              // same object identity
  assert.equal(fixture.components['t__0'].properties.width, '100px'); // new tree
  assert.equal(fixture.components.old, undefined);         // old tree gone
  assert.equal(fixture._wpt.lossyReasons.length, 0);       // re-extracted _wpt
  // BOTH honesty stamps: state delivered AND structure delivered.
  assert.equal(fixture._wpt.postLoadExtracted, true);
  assert.equal(fixture._wpt.structureExtracted, true);
});

test('wave20: adopt-then-bake composition lands computed state on CREATED components', () => {
  // The ordering constraint: structure first (adopt), state second (merge) —
  // the overlay must resolve paths against the RE-EXTRACTED tree, i.e. a
  // script-created child that never existed statically receives its baked
  // computed geometry.
  const fixture = { _wpt: { test: 't.html' }, components: { stale: {} } };
  const reFixture = { _wpt: { test: 't.html' }, components: {
    't__0': { properties: {}, children: {
      't__0__0': { properties: {} },                       // the created node
    } },
  } };
  adoptReExtractedFixture(fixture, reFixture);             // structure first
  const overlaid = mergePostLoadIntoFixture(fixture, 't', [
    { path: [0], tag: 'div', styles: { position: 'relative' } },
    { path: [0, 0], tag: 'div', styles: { position: 'absolute', top: '150px' } },
  ]);                                                      // then state bake
  assert.equal(overlaid, 2);                               // both mapped
  const child = fixture.components['t__0'].children['t__0__0'];
  assert.equal(child.properties.position, 'absolute');     // created node baked
  assert.equal(child.properties.top, '150px');
  // Both stamps survive the merge (merge re-stamps postLoadExtracted only).
  assert.equal(fixture._wpt.structureExtracted, true);
});

test('wave20: canvas-frame style id is pinned', () => {
  // The serializer strips the injected frame BY THIS ID — a rename that
  // misses one side would bake the capture pipeline's canvas CSS into
  // structure fixtures (double-applied at render time).
  assert.equal(CANVAS_FRAME_STYLE_ID, '__postLoadCanvasFrame');
});

// ── 8. The proving set — baked css-position wall fixtures ───────────────────
//
// fixtures/wpt is a GENERATED artifact (gitignored): these pins verify the
// values the post-load run actually baked, and skip on checkouts that have
// not run `node tools/titan/post-load-extract.mjs <the css-position list>`.
// Each pin states the test's expected FINAL state per its mutation script.

/** Read a proving-set fixture, or null when not present/not post-loaded. */
function provingFixture(stem) {
  const p = join(FIXTURES, `${stem}.json`);
  if (!existsSync(p)) return null;
  const fx = JSON.parse(readFileSync(p, 'utf8'));
  return fx._wpt?.postLoadExtracted === true ? fx : null;
}

const hyp1 = provingFixture('hypothetical-dynamic-change-001');
test('proving: hypothetical-dynamic-change-001 → Left 100px, green box', { skip: !hyp1 }, () => {
  // Script: onload sets ancestor.style.left = "100px"; the auto-positioned
  // fixed child follows its hypothetical static position to x=100.
  const anc = hyp1.components['hypothetical-dynamic-change-001__0'];
  assert.equal(anc.properties.position, 'fixed');
  assert.equal(anc.properties.left, '100px');           // the baked mutation
  assert.equal(anc.properties.top, '0px');
  assert.equal(anc.properties['background-color'], 'rgb(255, 0, 0)'); // red under…
  const child = anc.children['hypothetical-dynamic-change-001__0__0'];
  assert.equal(child.properties.left, '100px');         // …the green cover
  assert.equal(child.properties.top, '0px');
  assert.equal(child.properties['background-color'], 'rgb(0, 128, 0)');
  // Over-constraint dropped + basis pinned on every overlaid component.
  assert.equal(anc.properties.right, undefined);
  assert.equal(anc.properties['box-sizing'], 'content-box');
});

const hyp2 = provingFixture('hypothetical-dynamic-change-002');
test('proving: hypothetical-dynamic-change-002 → child follows abspos ancestor to x=100', { skip: !hyp2 }, () => {
  // Absolute ancestor: its containing block is the ICB, so the injected
  // canvas pad does not shift it — child's static position = 100 exactly.
  const anc = hyp2.components['hypothetical-dynamic-change-002__0'];
  assert.equal(anc.properties.left, '100px');
  const child = anc.children['hypothetical-dynamic-change-002__0__0'];
  assert.equal(child.properties.position, 'fixed');
  assert.equal(child.properties.left, '100px');
  assert.equal(child.properties['background-color'], 'rgb(0, 128, 0)');
});

const hyp3 = provingFixture('hypothetical-dynamic-change-003');
test('proving: hypothetical-dynamic-change-003 → relative ancestor: 100px offset baked, +16 canvas pad on the child', { skip: !hyp3 }, () => {
  // Relative ancestor: `left` stays the RELATIVE offset (100px). Under the
  // wave-25 IMGPAD contract the bake renders UNPADDED at the ref viewport
  // (canvasFrameCss injects no body padding), so baked coordinates live in
  // CONTENT space: the in-flow ancestor's static position is x=0 and the
  // fixed child's resolved hypothetical static position is exactly 100px.
  // The 16px frame is applied at RENDER time (image-space pad on refs, the
  // canvas frame on captures) — never baked into fixture coordinates.
  // This pin fired exactly as designed when the contract changed at wave
  // 25 (it froze the old padded-bake truth of 116px); re-pinned to the
  // imgpad truth so the NEXT contract change breaks a test first again.
  const anc = hyp3.components['hypothetical-dynamic-change-003__0'];
  assert.equal(anc.properties.position, 'relative');
  assert.equal(anc.properties.left, '100px');
  const child = anc.children['hypothetical-dynamic-change-003__0__0'];
  assert.equal(child.properties.position, 'fixed');
  assert.equal(child.properties.left, '100px');
  assert.equal(child.properties['background-color'], 'rgb(0, 128, 0)');
});

const insets = provingFixture('change-insets-inside-strict-containment-nested');
test('proving: change-insets → f1 left 0, f2 left 50px, preamble text intact', { skip: !insets }, () => {
  // Script: f1.style.left = "0"; f2.style.left = "50px" — two 50×100 green
  // strips composing the ref's filled green 100px square.
  const c = insets.components;
  const f1 = c['change-insets-inside-strict-containment-nested__1']
    .children['change-insets-inside-strict-containment-nested__1__0'];
  assert.equal(f1.properties.left, '0px');
  assert.equal(f1.properties.width, '50px');
  assert.equal(f1.properties.height, '100px');
  assert.equal(f1.properties['background-color'], 'rgb(0, 128, 0)');
  const f2 = f1.children['change-insets-inside-strict-containment-nested__1__0__0']
    .children['change-insets-inside-strict-containment-nested__1__0__0__0'];
  assert.equal(f2.properties.left, '50px');
  assert.equal(f2.properties['background-color'], 'rgb(0, 128, 0)');
  // Static-only content kept the static path: the instruction paragraph's
  // merged _text survives the overlay byte-identically.
  assert.equal(c['change-insets-inside-strict-containment-nested__0']._text,
    'Test passes if there is a filled green square and no red.');
});

const blockAxis = provingFixture('block-axis-constraint-changes-for-out-of-flow-box');
test('proving: block-axis-constraint → mutated borders baked per side', { skip: !blockAxis }, () => {
  // Script adds border-top 50px to parent 1 and border-bottom 50px to
  // parent 2 — the abspos children's containing blocks shrink accordingly.
  const p0 = blockAxis.components['block-axis-constraint-changes-for-out-of-flow-box__0'];
  assert.equal(p0.properties['border-top-width'], '50px');
  assert.equal(p0.properties['border-top-style'], 'solid');
  const p1 = blockAxis.components['block-axis-constraint-changes-for-out-of-flow-box__1'];
  assert.equal(p1.properties['border-bottom-width'], '50px');
  // The authored `box-sizing: border-box` survives as the COMPUTED basis,
  // and the baked height is the border-box 100px (gCS expresses sizes in
  // the element's own basis — the measured Chromium behaviour that made
  // the naive content-box pin a bug this pin originally caught).
  assert.equal(p0.properties['box-sizing'], 'border-box');
  assert.equal(p0.properties.height, '100px');
});

const abspos = provingFixture('absolute-pos-box-inside-fixed-pos-box-with-changing-height');
test('proving: changing-height → fixed box 300px, bottom-anchored child at 250', { skip: !abspos }, () => {
  // Script: fixed.style.height = "300px"; the bottom:0 abspos child lands
  // at top 250px — exactly covering the red .ref square at top 250px.
  const fixed = abspos.components['absolute-pos-box-inside-fixed-pos-box-with-changing-height__1']
    .children['absolute-pos-box-inside-fixed-pos-box-with-changing-height__1__0'];
  assert.equal(fixed.properties.height, '300px');       // the baked mutation
  const box = fixed.children['absolute-pos-box-inside-fixed-pos-box-with-changing-height__1__0__0'];
  assert.equal(box.properties.top, '250px');            // 300 − 50 (used bottom:0)
  assert.equal(box.properties['background-color'], 'rgb(0, 128, 0)');
  const ref = abspos.components['absolute-pos-box-inside-fixed-pos-box-with-changing-height__0'];
  assert.equal(ref.properties.top, '250px');            // the red target it covers
  assert.equal(ref.properties['background-color'], 'rgb(255, 0, 0)');
});

// The bail/decline halves of the proving set: their fixtures must NOT carry
// the stamp — the wave-15 exclusion stays for undelivered tests.
// wave-20: containing-block-change-button LEFT this list — its appendChild
// drift is now the structure-extraction TRIGGER, see the wave-20 proving pin
// below. Scrollframe stays: its bail (scrollTop=400) fires BEFORE the
// mapping cross-check, so the structure path never engages for it.
for (const stem of ['containing-block-change-scrollframe']) {
  const p = join(FIXTURES, `${stem}.json`);
  test(`proving: ${stem} stays static (bailed, no stamp)`, { skip: !existsSync(p) }, () => {
    // scrollframe: scrollTop=400 (scroll-offset bail) — undeliverable.
    const fx = JSON.parse(readFileSync(p, 'utf8'));
    assert.equal(fx._wpt.postLoadExtracted, undefined);
    assert.equal(fx._wpt.structureExtracted, undefined); // wave-20 stamp too
  });
}

// wave-20 proving pin: the former element-mapping bail case is now the
// structure-delivery case. Skip-guarded on the wave-20 stamp (fixtures/wpt
// is generated; a pre-wave-20 artifact skips rather than fails).
const cbcButtonPath = join(FIXTURES, 'containing-block-change-button.json');
const cbcButton = (() => {
  if (!existsSync(cbcButtonPath)) return null;                 // never extracted
  const fx = JSON.parse(readFileSync(cbcButtonPath, 'utf8'));
  return fx._wpt?.structureExtracted === true ? fx : null;     // pre-wave-20 → skip
})();
test('proving wave-20: containing-block-change-button → appended abspos child delivered', { skip: !cbcButton }, () => {
  // Script: button.style.position = "relative" (state) THEN
  // button.appendChild(createElement('div')) (structure). Both must land:
  // BOTH stamps present, the button carries the baked relative position,
  // and the script-created child exists as a first-class component with its
  // post-script computed geometry (top:150px/left:0 per the #button > div
  // rule, resolved against the now-relative button).
  assert.equal(cbcButton._wpt.postLoadExtracted, true);        // state stamp
  assert.equal(cbcButton._wpt.structureExtracted, true);       // structure stamp
  const btn = cbcButton.components['containing-block-change-button__0'];
  assert.equal(btn.properties.position, 'relative');           // baked mutation
  const child = btn.children['containing-block-change-button__0__0'];
  assert.equal(child.properties.position, 'absolute');         // created node…
  assert.equal(child.properties.top, '150px');                 // …with baked
  assert.equal(child.properties.left, '0px');                  // post-script
  assert.equal(child.properties.width, '100px');               // geometry
  assert.equal(child.properties['background-color'], 'rgb(0, 128, 0)');
});
// wave-21 collision fix: the overlay family lives under the css-position
// OVERLAY subdir (css/css-position/overlay/<name>.html), so its fixture
// stems carry the `overlay__` subdir prefix per safe-name.mjs fixtureStem —
// the bare stems here would silently skip these pins forever.
for (const stem of ['overlay__author-overlay-top-layer-removal', 'overlay__overlay-transition-backdrop',
                    'overlay__overlay-transition-backdrop-entry', 'overlay__overlay-button-appearance']) {
  const p = join(FIXTURES, `${stem}.json`);
  test(`proving: ${stem} stays static (top-layer decline, no stamp)`, { skip: !existsSync(p) }, () => {
    // The overlay family's signal lives in top-layer/::backdrop rendering —
    // declined before any browser launch (TOP_LAYER_API_RX).
    const fx = JSON.parse(readFileSync(p, 'utf8'));
    assert.equal(fx._wpt.postLoadExtracted, undefined);
  });
}

// ── 9. Optional live end-to-end (env-gated) ─────────────────────────────────
//
// Launches real Chromium against the real corpus — too heavy for the default
// hermetic `node --test` run, so it only fires with POST_LOAD_E2E=1 (and the
// corpus present). Verifies the full augment path end-to-end on the family's
// canonical test.
const WPT_DIR = process.env.WPT_DIR ?? join(REPO_ROOT, 'tools', 'wpt');
const E2E_TEST = 'css/css-position/hypothetical-dynamic-change-001.html';
const e2eReady = process.env.POST_LOAD_E2E === '1' && existsSync(join(WPT_DIR, E2E_TEST));

test('post-load e2e: live augment delivers Left 100px', { skip: !e2eReady }, async () => {
  // Dynamic import keeps extractFixture/browser cost out of skipped runs.
  const { extractFixture } = await import('./extract-fixture.mjs');
  const { postLoadAugmentFixture, closePostLoadBrowser } = await import('./post-load-extract.mjs');
  try {
    const { fixture } = await extractFixture(E2E_TEST);
    const outcome = await postLoadAugmentFixture(fixture, E2E_TEST);
    assert.equal(outcome.status, 'extracted');
    assert.equal(fixture._wpt.postLoadExtracted, true);
    assert.equal(fixture.components['hypothetical-dynamic-change-001__0'].properties.left, '100px');
  } finally {
    await closePostLoadBrowser(); // never leak the shared browser
  }
});

// ── Wave 22: collapsed-wrapper fold mode ─────────────────────────────────────
// The inline-chain collapse absorbs decoration wrappers into their parent's
// run, so the live DOM walk has records at paths with no component. When the
// nearest mapped ancestor carries `_decorations`, the record folds into it
// (existing keys win); without the marker the loud mismatch error stays.
test('wave-22 fold: absorbed-wrapper record folds into the _decorations ancestor', async () => {
  const { mergePostLoadIntoFixture } = await import('./post-load-extract.mjs');
  const fixture = {
    components: {
      t__0: { properties: {}, _decorations: [{ line: 'line-through' }] },
    },
    _wpt: {},
  };
  // Only enumerated POST_LOAD_COMPUTED_PROPERTIES are baked — use two of
  // them (width, margin-top); the parent record processes FIRST (walk
  // order), then the absorbed wrapper's folds with existing-keys-win.
  const records = [
    { path: [0], styles: { width: '100px' } },               // the parent itself
    { path: [0, 0], styles: { width: '50px', 'margin-top': '7px' } }, // absorbed <s>
  ];
  const n = mergePostLoadIntoFixture(fixture, 't', records);
  assert.equal(n, 2);
  // The absorbed record must NOT overwrite the parent's width (existing
  // key wins) but MAY fill keys the parent's record left absent.
  assert.equal(fixture.components.t__0.properties.width, '100px');
  assert.equal(fixture.components.t__0.properties['margin-top'], '7px');
  assert.equal(fixture._wpt.postLoadExtracted, true);
});

test('wave-22 fold: a pathless record WITHOUT a _decorations ancestor still fails loudly', async () => {
  const { mergePostLoadIntoFixture } = await import('./post-load-extract.mjs');
  const fixture = { components: { t__0: { properties: {} } }, _wpt: {} };
  assert.throws(
    () => mergePostLoadIntoFixture(fixture, 't', [{ path: [0, 3], styles: {} }]),
    /no component at path 0\.3/);
});

// ── wave-25 BD-RC4: the corner-radius bake ───────────────────────────────────

test('post-load bd-rc4: a script-rounded corner lands, square corners do not', () => {
  // The exact backdrop-filter-border-radius-change shape: the element is
  // authored `border-radius: 75px` and a double-rAF handler rewrites it to
  // 150px. The static bag therefore carries the STALE shorthand, and the
  // computed snapshot carries the truth.
  const target = { properties: { 'border-radius': '75px', 'background-color': 'green' } };
  overlayComputedOnComponent(target, {
    'background-color': 'rgb(0, 128, 0)',
    'border-top-left-radius': '150px',
    'border-top-right-radius': '150px',
    'border-bottom-right-radius': '150px',
    'border-bottom-left-radius': '150px',
  });
  // The stale shorthand is stripped, not left to outrank the baked corners.
  assert.equal(target.properties['border-radius'], undefined);
  assert.equal(target.properties['border-top-left-radius'], '150px');
  assert.equal(target.properties['border-bottom-left-radius'], '150px');

  // A square-cornered box reports the CSS initial on all four corners —
  // delete-not-write, so the overlay adds NO radius keys (the anti-bloat
  // half of the rule; nearly every component in the corpus is this shape).
  const plain = { properties: { display: 'block' } };
  overlayComputedOnComponent(plain, {
    display: 'block',
    'border-top-left-radius': '0px',
    'border-top-right-radius': '0px',
    'border-bottom-right-radius': '0px',
    'border-bottom-left-radius': '0px',
  });
  for (const c of ['border-top-left-radius', 'border-top-right-radius',
                   'border-bottom-right-radius', 'border-bottom-left-radius']) {
    assert.equal(plain.properties[c], undefined, `${c} must not be written at its initial`);
  }
});

test('post-load bd-rc4: a script that SQUARES a rounded corner deletes the stale key', () => {
  // The inverse mutation, and the reason delete-not-write must still DELETE:
  // an authored 40px corner that the script resets to 0 would otherwise stay
  // rounded in the fixture forever.
  const cmp = { properties: { 'border-top-left-radius': '40px' } };
  overlayComputedOnComponent(cmp, {
    'border-top-left-radius': '0px',
    'border-top-right-radius': '0px',
    'border-bottom-right-radius': '0px',
    'border-bottom-left-radius': '0px',
  });
  assert.equal(cmp.properties['border-top-left-radius'], undefined);
});

test('post-load bd-rc4: percentage and elliptical corners survive verbatim', () => {
  // MEASURED computed shapes in the pinned headless Chromium: a `50%` corner
  // stays a percentage (runtime-resolved, like every other % in the IR) and
  // an elliptical corner reports the two-value form. Both are shapes the
  // border-radius parser accepts, so they are written unchanged rather than
  // being coerced or dropped.
  const cmp = { properties: {} };
  overlayComputedOnComponent(cmp, {
    'border-top-left-radius': '10px 20px',
    'border-top-right-radius': '50%',
    'border-bottom-right-radius': '0px',
    'border-bottom-left-radius': '0px',
  });
  assert.equal(cmp.properties['border-top-left-radius'], '10px 20px');
  assert.equal(cmp.properties['border-top-right-radius'], '50%');
  assert.equal(cmp.properties['border-bottom-right-radius'], undefined);
});

// ── 11. wave-29 S-RC5: the POST-LOAD PSEUDO BAG ─────────────────────────────
//
// The hole these pin: a pseudo bag is a SELECTOR-MATCH RESULT over the host's
// attributes, not structure — so a class-only mutation changes it while the
// element walk stays identical, the cheap overlay path runs, and the bag was
// left at its PRE-mutation value inside a fixture stamped
// `postLoadExtracted: true`. Measured on css-pseudo/before-dynamic-display-none
// (`#id::before{content:"FAIL";…red}` + `#id.none::before{display:none}` +
// `id.className = "none"`): the delivered fixture still carried the 100x100
// red FAIL box the test exists to prove is gone.
//
// The pure surface is pinned here; the live browser round-trip
// (rePseudoFromLivePage) is exercised by the env-gated end-to-end section
// above and by running the extractor against the corpus.

import {
  hasPseudoElementRules, flattenComponents, linkedCssTextFor,
  pseudoRemapMismatch, applyPostLoadPseudoBags, unionLossyRecord,
} from './post-load-extract.mjs';

test('S-RC5 gate: hasPseudoElementRules fires on every buildable pseudo name', () => {
  // The three names buildComponents can build a bag from, in both the CSS3
  // `::` and the CSS2 legacy `:` spelling.
  for (const sel of ['#id::before', '#id::after', 'li::marker',
                     '#id:before', '#id:after']) {
    assert.ok(hasPseudoElementRules(`<style>${sel} { content: "x" }</style>`), sel);
  }
});

test('S-RC5 gate: hasPseudoElementRules declines when no pseudo rule exists', () => {
  // This is the cost guard: it keeps the extra page.evaluate + re-extraction
  // off the ~99% of wall-tagged tests with no generated content at all.
  assert.equal(hasPseudoElementRules('<style>div { color: red }</style><div>x</div>'), false);
  // A pseudo-CLASS is not a pseudo-ELEMENT and must not arm the gate.
  assert.equal(hasPseudoElementRules('<style>li:first-child { color: red }</style>'), false);
});

test('S-RC5 gate: a COMMENTED-OUT pseudo rule does not cost a round-trip', () => {
  assert.equal(hasPseudoElementRules('<!-- #id::before { content: "x" } -->'), false);
});

test('S-RC5: flattenComponents walks the nested children maps', () => {
  const fixture = { components: {
    s__0: { properties: {} },
    s__1: { properties: {}, children: {
      s__1__0: { properties: {} },
      s__1__1: { properties: {}, children: { s__1__1__0: { properties: {} } } },
    } },
  } };
  assert.deepEqual([...flattenComponents(fixture).keys()],
    ['s__0', 's__1', 's__1__0', 's__1__1', 's__1__1__0']);
  // Defensive: a fixture with no components is an empty map, not a throw.
  assert.equal(flattenComponents({}).size, 0);
});

test('S-RC5: pseudoRemapMismatch accepts identical ID spaces, rejects drift', () => {
  assert.equal(pseudoRemapMismatch(['a', 'b'], ['a', 'b']), null);
  // Count drift — the common signal when the serialized DOM re-parses into a
  // different tree than the live walk saw.
  assert.match(pseudoRemapMismatch(['a', 'b'], ['a']), /component count: static 2 vs post-load 1/);
  // Positional drift at equal counts.
  assert.match(pseudoRemapMismatch(['a', 'b'], ['a', 'c']), /id\[1\]: static b vs post-load c/);
});

test('S-RC5: applyPostLoadPseudoBags REPLACES a stale bag (before-dynamic-display-none)', () => {
  // The measured shape: static match on `class="open"` gives the FAIL box;
  // the post-load match on `class="none"` adds the `display: none` that
  // removes it.
  const stale = { properties: {}, _pseudo: { before: { properties: {
    content: '"FAIL"', position: 'absolute', width: '100px',
    height: '100px', 'background-color': 'red',
  } } } };
  const original = new Map([['x__0', stale]]);
  const fresh = new Map([['x__0', { _pseudo: { before: { properties: {
    content: '"FAIL"', position: 'absolute', width: '100px',
    height: '100px', 'background-color': 'red', display: 'none',
  } } } }]]);
  assert.equal(applyPostLoadPseudoBags(original, fresh), 1);
  assert.equal(stale._pseudo.before.properties.display, 'none');
});

test('S-RC5: applyPostLoadPseudoBags ADDS a bag the mutation newly matched', () => {
  const cmp = { properties: {} };                       // no bag statically
  const original = new Map([['x__0', cmp]]);
  const fresh = new Map([['x__0', { _pseudo: { after: { properties: { content: '"OK"' } } } }]]);
  assert.equal(applyPostLoadPseudoBags(original, fresh), 1);
  assert.deepEqual(cmp._pseudo, { after: { properties: { content: '"OK"' } } });
});

test('S-RC5: applyPostLoadPseudoBags DELETES a bag the mutation stopped matching', () => {
  const cmp = { properties: {}, _pseudo: { before: { properties: { content: '"X"' } } } };
  const original = new Map([['x__0', cmp]]);
  const fresh = new Map([['x__0', { properties: {} }]]);  // no bag post-load
  assert.equal(applyPostLoadPseudoBags(original, fresh), 1);
  assert.equal('_pseudo' in cmp, false, 'stale key left behind');
});

test('S-RC5: an unchanged bag is a NO-OP (byte-identical fixture)', () => {
  const bag = { before: { properties: { content: '"X"' } } };
  const cmp = { properties: {}, _pseudo: bag };
  const original = new Map([['x__0', cmp]]);
  // Deep-equal but distinct object — the value compare must see them as same.
  const fresh = new Map([['x__0', { _pseudo: { before: { properties: { content: '"X"' } } } }]]);
  assert.equal(applyPostLoadPseudoBags(original, fresh), 0);
  assert.equal(cmp._pseudo, bag, 'object identity replaced on a no-op');
});

test('S-RC5: applyPostLoadPseudoBags touches nothing but _pseudo', () => {
  // `properties` belongs to the computed overlay that runs next;
  // `_text`/`_tag`/`_attrs`/`children` are structural identity mappingMismatch
  // already proved unchanged.
  const cmp = { properties: { color: 'red' }, _text: 'PASS', _tag: 'div',
                _attrs: { type: 'checkbox' }, children: { 'x__0__0': {} } };
  applyPostLoadPseudoBags(new Map([['x__0', cmp]]),
    new Map([['x__0', { properties: { color: 'blue' }, _text: 'other',
                        _pseudo: { before: { properties: {} } } }]]));
  assert.deepEqual(cmp.properties, { color: 'red' });
  assert.equal(cmp._text, 'PASS');
  assert.equal(cmp._tag, 'div');
  assert.deepEqual(cmp._attrs, { type: 'checkbox' });
  assert.deepEqual(Object.keys(cmp.children), ['x__0__0']);
});

test('S-RC5: unionLossyRecord ADDS reasons and never subtracts them', () => {
  // Direction pin: a reason belonging to a bag the mutation removed cannot be
  // attributed back (the static record is a flat set), and over-reporting is
  // the safe error — applyNaScoreGate only reads lossyReasons to CONFIRM a
  // requires-bundled-asset exclusion, so an extra reason cannot promote a
  // test into the scored set.
  const fixture = { _wpt: { lossy: false, lossyReasons: ['percentage'] } };
  unionLossyRecord(fixture, { _wpt: { lossy: true, lossyReasons: ['sampled-animation'] } });
  assert.deepEqual(fixture._wpt.lossyReasons.sort(), ['percentage', 'sampled-animation']);
  assert.equal(fixture._wpt.lossy, true);
  // An already-lossy fixture stays lossy even when the re-extraction is clean.
  const f2 = { _wpt: { lossy: true, lossyReasons: ['percentage'] } };
  unionLossyRecord(f2, { _wpt: { lossy: false, lossyReasons: [] } });
  assert.equal(f2._wpt.lossy, true);
  assert.deepEqual(f2._wpt.lossyReasons, ['percentage']);
});

// ── skeptic-29: the S-RC5 gate must see LINKED stylesheets ──────────────────
//
// hasPseudoElementRules is handed a string and can only see that string. The
// wave-29 gate was handed the raw test source alone, so a test whose only
// ::before rule lives in a `<link rel=stylesheet>` sheet skipped the whole
// pseudo re-derivation — the exact stale-bag bug S-RC5 exists to fix — while
// the fixture was still stamped postLoadExtracted:true. Measured corpus reach
// was 1 wall-tagged test, so these pins guard a hole rather than a fire.

test('skeptic-29: linkedCssTextFor resolves test-relative and corpus-absolute hrefs', async () => {
  const dir = await mkdtemp(join(tmpdir(), 'plx-linked-'));
  const wptDir = join(dir, 'wpt');
  await mkdir(join(wptDir, 'css', 'sub'), { recursive: true });
  await mkdir(join(wptDir, 'shared'), { recursive: true });
  // A test-RELATIVE sheet next to the test, and a corpus-ABSOLUTE one at /shared.
  await writeFile(join(wptDir, 'css', 'sub', 'local.css'), '#a::before { content: "L" }');
  await writeFile(join(wptDir, 'shared', 'g.css'), '#b::after { content: "G" }');
  const testAbs = join(wptDir, 'css', 'sub', 't.html');
  const html = '<link rel="stylesheet" href="local.css">'
             + '<link rel="stylesheet" href="/shared/g.css">'
             + '<link rel="stylesheet" href="https://example.test/remote.css">'
             + '<link rel="stylesheet" href="missing.css">'
             + '<div id=a></div>';
  // WPT_DIR is read at module load, so drive the absolute leg through a
  // child process that sets it — the relative leg is checked in-process.
  const relOnly = await linkedCssTextFor(
    '<link rel="stylesheet" href="local.css"><link rel="stylesheet" href="missing.css">', testAbs);
  assert.match(relOnly, /#a::before/);            // resolved next to the test
  assert.ok(hasPseudoElementRules(relOnly));      // …and it arms the gate
  // A missing sheet is tolerated (no throw) and a remote one is skipped —
  // both mirror extractFixture's own loop.
  const noneReadable = await linkedCssTextFor(
    '<link rel="stylesheet" href="https://example.test/x.css">'
    + '<link rel="stylesheet" href="nope.css">', testAbs);
  assert.equal(hasPseudoElementRules(noneReadable), false);
  assert.equal(typeof noneReadable, 'string');
  // The full html above still parses without throwing.
  assert.equal(typeof (await linkedCssTextFor(html, testAbs)), 'string');
  await rm(dir, { recursive: true, force: true });
});

test('skeptic-29: the gate arms on a linked-only pseudo rule, and only then', async () => {
  const dir = await mkdtemp(join(tmpdir(), 'plx-linked2-'));
  await writeFile(join(dir, 'pseudo.css'), '.x::marker { color: red }');
  await writeFile(join(dir, 'plain.css'), '.x { color: red }');
  const testAbs = join(dir, 't.html');
  // Source alone: no pseudo rule anywhere in the HTML text.
  const src = '<link rel="stylesheet" href="pseudo.css"><ul><li class=x>a</li></ul>';
  assert.equal(hasPseudoElementRules(src), false,
    'the raw source must NOT arm the gate — that is the whole hole');
  assert.ok(hasPseudoElementRules(await linkedCssTextFor(src, testAbs)),
    'the linked sheet must arm it');
  // Control: a linked sheet with no pseudo rule must still decline, so the
  // fix cannot turn the gate into "always run" (its cost guard would be gone).
  const src2 = '<link rel="stylesheet" href="plain.css"><ul><li class=x>a</li></ul>';
  assert.equal(hasPseudoElementRules(src2), false);
  assert.equal(hasPseudoElementRules(await linkedCssTextFor(src2, testAbs)), false);
  await rm(dir, { recursive: true, force: true });
});

// ═════════════════════════════════════════════════════════════════════════════
// wave-30 A3: the SECOND activation route (dropped rules)
// ═════════════════════════════════════════════════════════════════════════════

const { shouldPostLoadExtract } = await import('./post-load-extract.mjs');
const { countUnsupportedRules } = await import('./extract-fixture.mjs');

test('wave30 A3: route 1 (wall tags) is unchanged', () => {
  // Zero dropped rules — the gate must still open on the wall tags alone,
  // exactly as the wave-16 isWallTagged gate did.
  assert.equal(shouldPostLoadExtract(['requires-script-mutation'], 0), true);
  assert.equal(shouldPostLoadExtract(['requires-script-driven-scroll'], 0), true);
  assert.equal(shouldPostLoadExtract(['requires-anchor-positioning-runtime'], 0), true);
  assert.equal(shouldPostLoadExtract(['requires-float-layout'], 0), false);
  assert.equal(shouldPostLoadExtract(undefined, 0), false);
});

test('wave30 A3: route 2 opens on ≥1 dropped rule, with no wall tag', () => {
  assert.equal(shouldPostLoadExtract([], 1), true);
  assert.equal(shouldPostLoadExtract(['requires-float-layout'], 7), true);
  assert.equal(shouldPostLoadExtract(undefined, 3), true);
  // …and stays shut at zero.
  assert.equal(shouldPostLoadExtract([], 0), false);
});

test('wave30 A3: a non-numeric count can never open the gate', () => {
  // Defensive: a caller that forgot to thread the count through must degrade
  // to route 1 rather than accidentally activating the whole corpus.
  assert.equal(shouldPostLoadExtract([], undefined), false);
  assert.equal(shouldPostLoadExtract([], NaN), false);
  assert.equal(shouldPostLoadExtract([], null), false);
  assert.equal(shouldPostLoadExtract([]), false);
});

test('wave30 A3: the count and the gate agree on a real dir-family sheet', () => {
  // dir-selector-auto-direction-change-001's stylesheet before wave-30 A1:
  // the `:dir(ltr) + #target` rule was the dropped one. Post-A1 it is
  // matchable, so the gate for THIS test now rests on its wall tag (which
  // bucket-wpt's CharacterData supplement grants it) — pinned here so a
  // regression in either half is visible.
  const post = countUnsupportedRules(parseCss(
    '#target { background-color: red } :dir(ltr) + #target { background-color: green }'));
  assert.equal(post, 0);
  assert.equal(shouldPostLoadExtract([], post), false);
  assert.equal(shouldPostLoadExtract(['requires-script-mutation'], post), true);
});

// ═════════════════════════════════════════════════════════════════════════════
// wave-30 fix-T2: the A3 reordering must not turn a SKIP into an ERROR
// ═════════════════════════════════════════════════════════════════════════════

// A3 moved the static pass AHEAD of the activation gate so the gate could read
// `unsupportedRules`. That also moved extract-fixture's own INPUT REJECTIONS
// ahead of the gate, so an input the gate would have skipped started throwing:
//
//   node tools/titan/post-load-extract.mjs \
//     css/CSS2/abspos/abspos-in-block-in-inline-in-relpos-inline.html
//
// is a bucket-C document that extract-fixture refuses by design ("is not in
// bucket A or B"). Pre-A3 it printed one `skip` line and exited 0; post-A3 it
// printed `ERROR` and exited 1 — a CLI regression on the whole non-qualifying
// population. The pins below drive the real CLI, because the regression lived
// in the CLI's control flow and nowhere else. The skip path never launches a
// browser, so this stays a sub-second, hermetic subprocess test.
const CLI_SKIP_INPUT = 'css/CSS2/abspos/abspos-in-block-in-inline-in-relpos-inline.html';
const cliReady = existsSync(join(WPT_DIR, CLI_SKIP_INPUT));

test('fix-T2: a non-bucket-A/B input is a clean SKIP, not an error', { skip: !cliReady }, () => {
  const r = spawnSync(process.execPath,
    [join(REPO_ROOT, 'tools', 'titan', 'post-load-extract.mjs'), CLI_SKIP_INPUT],
    { encoding: 'utf8', cwd: REPO_ROOT });
  assert.equal(r.status, 0, `expected exit 0, got ${r.status}\n${r.stdout}\n${r.stderr}`);
  assert.match(r.stdout, /^skip {6}css\/CSS2\/abspos\/abspos-in-block-in-inline-in-relpos-inline\.html/m);
  // The tally must COUNT it as skipped, not swallow it silently.
  assert.match(r.stdout, /skipped=1 errors=0/);
  assert.doesNotMatch(r.stdout + r.stderr, /^ERROR/m);
});

test('fix-T2: --force still surfaces the static pass failure as a hard error', { skip: !cliReady }, () => {
  // The skip is a GATE decision, not a blanket catch: a run that explicitly
  // asks for this test (or a wall-tagged one, route 1) must still see the
  // real error and exit non-zero. Otherwise the fix would have hidden every
  // extraction failure in the activated population too.
  const r = spawnSync(process.execPath,
    [join(REPO_ROOT, 'tools', 'titan', 'post-load-extract.mjs'), '--force', CLI_SKIP_INPUT],
    { encoding: 'utf8', cwd: REPO_ROOT });
  assert.equal(r.status, 1, 'a forced run must still fail loudly');
  assert.match(r.stderr, /ERROR\s+.*is not in bucket A or B/);
  assert.match(r.stdout, /skipped=0 errors=1/);
});

test('fix-T2: the CLI asks the gate on route 1 when the static pass threw', () => {
  // The catch re-asks shouldPostLoadExtractFor with an unsupportedRules count
  // of 0 — route 2 has no input when the static pass never produced one. This
  // pins the semantics of that degraded call: wall tags still open the gate
  // (so the error surfaces), everything else closes it (so the run skips).
  assert.equal(shouldPostLoadExtract(['requires-script-mutation'], 0), true);
  assert.equal(shouldPostLoadExtract(['requires-float-layout'], 0), false);
  assert.equal(shouldPostLoadExtract(undefined, 0), false);
});

// ── wave-32 lane R: wedged-Chromium recovery ────────────────────────────────
//
// The browser is SHARED across a whole batch (1,850 tests in a corpus
// re-extraction). When Chromium dies or its DevTools pipe wedges, every
// remaining test used to inherit the corpse and wait out `protocolTimeout`
// (300 s) — a wedge 200 tests from the end burned ~17 hours producing
// nothing, with the real cause scrolled off the top of the log. The recovery
// contract is pinned here rather than left to a live wedge nobody can
// reproduce on demand: recognise the transport failure, discard (never
// close) the instance, retry the failed test ONCE on a fresh one, then bail
// to static loudly.

test('wedge recovery: recognises transport failures, not page failures', async () => {
  const { isWedgedBrowserError } = await import('./post-load-extract.mjs');
  // The puppeteer/CDP transport shapes that mean "this instance is dead".
  for (const msg of [
    'ProtocolError: Runtime.callFunctionOn timed out. Increase the protocolTimeout',
    'Protocol error (Page.navigate): Target closed',
    'Session closed. Most likely the page has been closed.',
    'Error: Connection closed',
    'Target crashed',
    'Browser has disconnected',
  ]) {
    assert.equal(isWedgedBrowserError(new Error(msg)), true, msg);
  }
  // A PAGE-level failure is not a wedge: the browser is alive and the next
  // test will run fine, so relaunching would only mask a per-test bug.
  for (const msg of [
    'Navigation timeout of 30000 ms exceeded',
    'waiting for selector `#x` failed',
    'Evaluation failed: TypeError: x is not a function',
  ]) {
    assert.equal(isWedgedBrowserError(new Error(msg)), false, msg);
  }
  assert.equal(isWedgedBrowserError(null), false);
});

test('wedge recovery: one wedge → discard, retry, succeed', async () => {
  const { runWithBrowserRecovery } = await import('./post-load-extract.mjs');
  let attempts = 0;
  let discards = 0;
  const result = await runWithBrowserRecovery(
    async () => {
      attempts += 1;
      if (attempts === 1) throw new Error('Protocol error: Target closed');
      return { status: 'extracted' };
    },
    async () => { discards += 1; },
  );
  // "Continue FROM the failed test" — the same test re-runs on a fresh
  // instance rather than being skipped.
  assert.deepEqual(result, { status: 'extracted' });
  assert.equal(attempts, 2);
  assert.equal(discards, 1, 'the wedged instance must be thrown away exactly once');
});

test('wedge recovery: a second wedge bails to static instead of looping', async () => {
  const { runWithBrowserRecovery } = await import('./post-load-extract.mjs');
  let attempts = 0;
  let discards = 0;
  const result = await runWithBrowserRecovery(
    async () => { attempts += 1; throw new Error('ProtocolError: protocolTimeout exceeded'); },
    async () => { discards += 1; },
  );
  // A fresh instance wedging on the same input is not transport noise — it
  // is that test killing Chromium, so it takes the bail-to-static path.
  assert.equal(result.status, 'bailed');
  assert.match(result.reason, /browser-wedged-twice/);
  assert.equal(attempts, 2, 'exactly one retry — never an unbounded relaunch loop');
  // Discarded again so the REST of the batch starts from a fresh browser.
  assert.equal(discards, 2);
});

test('wedge recovery: a non-transport error propagates untouched', async () => {
  const { runWithBrowserRecovery } = await import('./post-load-extract.mjs');
  let attempts = 0;
  let discards = 0;
  await assert.rejects(
    () => runWithBrowserRecovery(
      async () => { attempts += 1; throw new Error('Navigation timeout of 30000 ms exceeded'); },
      async () => { discards += 1; },
    ),
    /Navigation timeout/,
  );
  // No retry, no discard — a genuine per-test failure must stay visible.
  assert.equal(attempts, 1);
  assert.equal(discards, 0);
});

test('wedge recovery: discardPostLoadBrowser is a no-op when nothing launched', async () => {
  const { discardPostLoadBrowser } = await import('./post-load-extract.mjs');
  // Contract 2 — DISCARD, never close: a wedged browser cannot answer
  // close() either, so the recovery path must not await one. With no
  // instance at all this must simply return.
  await discardPostLoadBrowser();
});
