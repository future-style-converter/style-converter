#!/usr/bin/env node
//
// tools/titan/post-load-extract.mjs — wave-16 POST-LOAD extraction mode.
//
// THE EXTRACTION WALL, CROSSED: the static extractor (extract-fixture.mjs)
// parses test HTML without executing scripts, so every test whose visual
// output depends on post-load script execution (style mutation, top-layer
// promotion, script-driven scroll) renders its PRE-mutation state. wave-15
// made that honest by score-excluding EXTRACTION_WALL_TAGS tests
// (inject-wpt-block.mjs); this module makes a subset of them SCORABLE again
// by actually delivering the post-script state:
//
//   1. load the TEST page (not the ref) in headless Chromium — the same
//      browser, launch flags, canvas frame, font pins, and settle sequence
//      the browser-ref capture uses (capture-browser-ref.mjs), so computed
//      geometry is snapshotted under the identical rendering contract;
//   2. after load + fonts.ready + double-rAF settle, snapshot per-element
//      COMPUTED state (getComputedStyle over a deliberately enumerated
//      box-relevant property set, POST_LOAD_COMPUTED_PROPERTIES below) plus
//      getBoundingClientRect as a cross-check, walking the DOM with the
//      SAME traversal identity the static extractor uses;
//   3. computed values OVERRIDE the statically-parsed properties per
//      element (computed px are absolute, so they land as plain px
//      declarations the converter already handles); static-only content
//      (`_text`, the synthetic `__body`/`__text` components) keeps the
//      static path. wave-29 S-RC5: `_pseudo` left that list — a pseudo bag
//      is a SELECTOR-MATCH RESULT over the element's attributes, and a
//      class-only mutation (the commonest WPT dynamic-reftest shape) changes
//      it without changing structure, so the bags are re-derived from the
//      settled DOM through the same serialize → re-extract round-trip the
//      structure path uses (see the S-RC5 banner);
//   4. the fixture is stamped `_wpt.postLoadExtracted: true`; the
//      wpt-buckets.json `requires-script-mutation` tag stays untouched for
//      provenance, and inject-wpt-block's applyNaScoreGate re-scores the
//      test on the stamp (the wall was the inability to deliver the
//      post-script state; now delivered).
//
// SCOPE BOUNDARY (documented, enforced, not silent):
//   - TIME-STABLE ONLY. WPT dynamic reftests settle synchronously on load
//     (mutate in <script> or onload, then paint once). Tests with timers /
//     rAF loops / running transitions of box properties are NOT time-stable
//     — detected by the settle-stability check (two snapshots 100 ms apart
//     must be IDENTICAL), which bails to the static path and keeps the
//     wave-15 exclusion.
//   - NO TOP LAYER. showPopover()/showModal()/requestFullscreen() promote
//     elements into the browser's top layer, where the rendered output
//     (::backdrop boxes, top-layer paint order, overlay-transition
//     retention) lives OUTSIDE per-element computed style — a snapshot of
//     the element's own properties cannot represent it (measured: the
//     overlay-transition-backdrop pair's whole signal is a green ::backdrop
//     on an otherwise visibility:hidden element). Declined by static source
//     scan (TOP_LAYER_API_RX) BEFORE launching a browser; conservative on
//     purpose — a runtime :popover-open probe would MISS the post-hide
//     overlay-transition state that keeps an element in the top layer.
//   - STRUCTURAL MUTATION → STRUCTURE RE-EXTRACTION (wave 20). appendChild /
//     createElement / removeChild mutations change the component TREE, not
//     just property values, so the computed overlay has no static counterpart
//     to land on. The element-mapping cross-check still detects the drift —
//     but for tests wall-tagged `requires-script-mutation` the former bail
//     branch is now the TRIGGER: the live post-script DOM is serialized
//     (body outerHTML + original head, scripts stripped) and fed through the
//     SAME extract-fixture.mjs pipeline via its `htmlOverride` input, so
//     created nodes become first-class components and removed nodes vanish.
//     The 34-property state bake then re-runs against the same live page,
//     mapped by a SECOND traversal walk whose static side is the serialized
//     DOM — which matches the live DOM by construction (it was generated
//     from it). Fixtures produced this way carry BOTH honesty stamps:
//     `_wpt.postLoadExtracted` and `_wpt.structureExtracted`. Mismatches on
//     tests NOT tagged `requires-script-mutation` (e.g. scroll-only walls,
//     --force runs) keep the wave-16 bail — the trigger is scoped to the
//     population whose wall IS structural mutation.
//   - NO SCROLL OFFSETS. The IR has no scroll-position model; if any
//     element (or the document) sits at a non-zero scroll offset after
//     settle, the visual state depends on it and we bail.
//   - NO PRE-ALIGNMENT ANCHOR INSETS (wave 29). The bake's premise is that
//     an out-of-flow box's CSSOM resolved inset IS its used inset. For
//     ANCHOR-ALIGNED boxes that premise fails: Chromium applies the
//     anchor-center alignment shift during layout and never folds it back
//     into the serialized value, so `left` can resolve "6px" on a box that
//     paints 24px in. anchorInsetMismatch measures resolved-vs-painted per
//     anchored box and bails (`anchor-inset-undeliverable`) rather than
//     stamping a fixture "delivered" with the box in the wrong place —
//     the test keeps its wall exclusion, which is the honest outcome.
//     Scoped to anchor-flavoured alignment so no pre-wave-29 population
//     changes behaviour.
//
// ACTIVATION (opt-in): extract-fixture.mjs's CLI invokes this module only
// when POST_LOAD_EXTRACT=1 (or --post-load) is set AND the test carries an
// EXTRACTION_WALL_TAGS tag in wpt-buckets.json's notApplicable map — the
// exact population whose scores the wall gate excludes. This module's own
// CLI does the same end-to-end for an explicit test list (the proving set).
//
// Usage:
//   node tools/titan/post-load-extract.mjs <relative-test-path>...
//   POST_LOAD_EXTRACT=1 node tools/titan/extract-fixture.mjs <paths>...
//
// Exit codes: 0 — every input handled (extracted OR an expected
// decline/bail); 1 — at least one hard error (browser/IO failure).

import puppeteer from 'puppeteer';
import { promises as fs } from 'node:fs';
import { resolve, dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

// The static extractor's own building blocks — reusing them (not copies) is
// what guarantees the browser walk and the static walk can't drift apart.
import {
  stripComments, extractInlineStyle, extractLinkedStylesheets, parseCss,
  collectStyledTags, extractBodyTreeNested,
  HEAD_ONLY_TAGS, INLINE_MERGE_TAGS,
  // fix 5: the non-XHTML-namespace marker the serializer stamps and
  // buildNode() gates `_tag`/`_attrs` on — ONE constant, two consumers.
  FOREIGN_NS_MARKER_ATTR,
  extractFixture, writeFixturePair,
} from './extract-fixture.mjs';
// The ONE canonical fixture-stem derivation (wave-21 collision fix): the
// overlay/structure paths reconstruct component ids as `<stem>__N…` and must
// use the SAME subdir-encoded stem extractFixture seeded buildComponents
// with — see the two derivation sites in postLoadAugmentFixture.
import { fixtureStem } from './safe-name.mjs';
// The browser-ref capture's rendering contract: same launch flags, same
// 358-wide unpadded white canvas, same Inter face embed + line-height pin —
// computed geometry must be measured under the environment the ref PNGs
// (and the harness captures) are produced in, or every overridden inset
// would carry a systematic offset (corpus-v4.1 font-pin lesson).
// wave-25 round 3: the frame sheet + the viewport numbers are IMPORTED, not
// re-typed. The old hand-copied literal here still injected the pre-CAL-RC1
// `:where(body){padding:16px}` at viewport 390 while the ref had already
// moved to a zero-pad 358-wide render — a silent 32px surplus in every
// viewport-relative computed value this module bakes.
import {
  BROWSER_LAUNCH_ARGS, canvasFrameCss,
  REF_RENDER_WIDTH, REF_RENDER_MIN_HEIGHT,
} from './capture-browser-ref.mjs';
// The scoring authority's wall-tag set — activation must use the EXACT tags
// the gate excludes on, or the two would disagree about which tests need
// post-load delivery.
import { EXTRACTION_WALL_TAGS } from './inject-wpt-block.mjs';

const __filename = fileURLToPath(import.meta.url);
const __dirname  = dirname(__filename);
const REPO_ROOT  = resolve(__dirname, '..', '..');
// Same corpus root override the other titan tools honour (unit tests point
// WPT_DIR at scratch corpora).
const WPT_DIR    = process.env.WPT_DIR ?? join(REPO_ROOT, 'tools', 'wpt');

// ── The enumerated computed-property set ─────────────────────────────────────
//
// DELIBERATE enumeration (not "all of getComputedStyle") — every entry has a
// reason to exist, and everything NOT here deliberately keeps its static
// value so the overlay stays reviewable. All values come back from Chromium
// as resolved/used values (px lengths, rgb() colors) that the converter's
// existing primitive parsers already handle.
export const POST_LOAD_COMPUTED_PROPERTIES = [
  // The css-position lane's subject: what the mutation scripts change.
  'position',
  // Insets — resolved to USED px for out-of-flow boxes (CSSOM resolved-value
  // rules), which is exactly the "hypothetical static position" answer the
  // dynamic-change family exists to test. 'auto' (statically-positioned
  // elements) is delete-not-write, see WRITE_RULES.
  'top', 'right', 'bottom', 'left',
  // Used size in px — expressed in the element's OWN box-sizing basis
  // (MEASURED in headless Chromium: `box-sizing:border-box; height:100px;
  // border-top:50px` reports height "100px" — the border-box size — while
  // the content-box twin with the same markup reports its content 100px).
  // `box-sizing` below rides along so the basis is always explicit and the
  // runtime re-interprets the number exactly as the browser meant it.
  'width', 'height', 'box-sizing',
  // Box construction — margins/padding always resolve to px; needed because
  // scripts mutate them (block-axis-constraint mutates borders) and because
  // width/height above are content-box measures.
  'margin-top', 'margin-right', 'margin-bottom', 'margin-left',
  'padding-top', 'padding-right', 'padding-bottom', 'padding-left',
  // Border WIDTH per the task's box-relevant set — plus STYLE and COLOR per
  // side, because the merge strips conflicting `border`/`border-*`
  // shorthands from the static bag (SHORTHAND_CONFLICTS): stripping a
  // shorthand while only re-writing widths would silently drop the ink.
  'border-top-width', 'border-right-width', 'border-bottom-width', 'border-left-width',
  'border-top-style', 'border-right-style', 'border-bottom-style', 'border-left-style',
  'border-top-color', 'border-right-color', 'border-bottom-color', 'border-left-color',
  // The pass/fail ink of virtually every reftest (green vs red).
  'background-color',
  // Scripts toggle transforms in the wider wall corpus; computed matrix()
  // is absolute. 'none' is delete-not-write.
  'transform',
  // RC-A5a (wave 19): self-alignment — the css-flexbox dynamic-change
  // family mutates it (abspos/dynamic-align-self-001: `child.style.alignSelf
  // = 'start'` after load). The baked USED insets already encode the
  // post-mutation position, but the STALE static declaration lingered
  // underneath ('align-self: end' survived the overlay) and a runtime that
  // gives self-alignment priority over the baked insets faithfully rendered
  // the PRE-mutation state (wave18-final: web-ref 0.999 / ios-ref 0.997 /
  // android-ref 0.9416 on the same fixture). Snapshotting the computed
  // keyword delivers the post-script value to every runtime and displaces
  // the stale key. 'auto' (the css-align-3 §6.1 initial) is delete-not-write.
  'align-self',
  // display gates box generation (script-driven display flips are a whole
  // wall family); overflow-x/y as LONGHANDS (the single `overflow` static
  // shorthand is stripped); z-index orders the overlapping boxes these
  // tests paint ('auto' is delete-not-write).
  'display', 'overflow-x', 'overflow-y', 'z-index',
  // wave-24 B-RC4: the css-lists dynamic family's subjects. Both are
  // INHERITED (CSS Lists 3 §3), and the mutation is typically made on an
  // ANCESTOR — css-lists/change-list-style-position-003 runs
  // `document.body.style.listStylePosition = "inside"` after load, so no
  // element the static extractor can see ever declares it, and the marker
  // stays `outside` in the fixture while the ref paints it `inside`.
  // Snapshotting the COMPUTED value delivers the inherited post-script
  // state to every element that actually renders a marker. Both are
  // delete-not-write at their CSS-initial values (MEASURED in the pinned
  // headless Chromium against both css-lists change-* tests: a plain
  // `<div>`/`<body>` reports `list-style-type: disc` /
  // `list-style-position: outside`), so the ~all non-list elements of the
  // corpus gain no key and the overlay stays reviewable — while a `<ol>`
  // (computed `decimal`) or an `inside` marker writes explicitly.
  'list-style-type', 'list-style-position',
  // wave-25 BD-RC4: the corner radii. filter-effects/
  // backdrop-filter-border-radius-change is the motivating test — it declares
  // `border-radius: 75px` (and 5px on the inline twin), then on a
  // double-rAF after load rewrites BOTH to 150px. The static extractor only
  // ever sees the pre-mutation 75px/5px, so the capture painted the wrong
  // corners against a ref drawn at 150px. Computed corner radii resolve to
  // absolute px per corner, so snapshotting them delivers the post-script
  // state to all three runtimes.
  // MEASURED in the pinned headless Chromium (probe over a plain div, a
  // `border-radius:75px` div, a `50%` div, an elliptical
  // `border-top-left-radius:10px 20px` div and a `border-radius:5px` span):
  // the plain div reports '0px' on all four corners; `75px` reports '75px';
  // a percentage stays '50%' (runtime-resolved, exactly like every other
  // percentage in the IR); an elliptical corner reports the two-value
  // '10px 20px' form. All of those are shapes the border-radius parser
  // already accepts, and only '0px' is delete-not-write (below).
  'border-top-left-radius', 'border-top-right-radius',
  'border-bottom-right-radius', 'border-bottom-left-radius',
];

// Per-property write rules for the merge. Default (not listed) = write the
// computed value unconditionally. The listed exceptions encode "the computed
// default carries no declaration" — writing them would bloat every component
// with noise; but the matching STATIC key is still DELETED so a stale
// pre-mutation value can never linger underneath.
export const WRITE_RULES = {
  top:        { deleteWhen: 'auto' },   // static-position boxes: no inset declaration
  right:      { deleteWhen: 'auto' },
  bottom:     { deleteWhen: 'auto' },
  left:       { deleteWhen: 'auto' },
  'z-index':  { deleteWhen: 'auto' },   // auto = unstacked, not "0"
  transform:  { deleteWhen: 'none' },   // none = no transform declaration
  // RC-A5a: 'auto' is align-self's initial value (css-align-3 §6.1 — defer
  // to the parent's align-items) and carries no declaration; but the static
  // key is still deleted so a stale pre-mutation keyword cannot linger.
  'align-self': { deleteWhen: 'auto' },
  width:      { requirePx: true },      // 'auto' (e.g. display:none) → delete
  height:     { requirePx: true },
  // wave-24 B-RC4: the CSS-initial values of the two list properties (CSS
  // Lists 3 §3.1/§3.2 — `disc` and `outside`), MEASURED as what Chromium
  // reports for a non-list element in the pinned headless build. They carry
  // no declaration, so writing them would stamp a list-marker property onto
  // every div in the corpus; the matching static key is still DELETED so a
  // stale pre-mutation `list-style-position: outside` cannot linger under a
  // script that switched it (the whole change-list-style-position-003
  // failure mode).
  'list-style-type':     { deleteWhen: 'disc' },
  'list-style-position': { deleteWhen: 'outside' },
  // wave-25 BD-RC4: '0px' is the CSS-initial corner radius (CSS Backgrounds 3
  // §5.1 — initial `0`), and it is what the pinned headless Chromium reports
  // for every un-rounded element (MEASURED, see the property list above). The
  // overwhelming majority of the corpus is square-cornered, so writing it
  // would stamp four radius keys onto essentially every component and drown
  // the overlay diff. Delete-not-write keeps the fixture reviewable while
  // still removing any stale static key — which is exactly what
  // backdrop-filter-border-radius-change needs when a script rounds a corner
  // that started square, and the inverse (a script SQUARING a rounded corner)
  // would otherwise leave the authored 75px standing.
  'border-top-left-radius':     { deleteWhen: '0px' },
  'border-top-right-radius':    { deleteWhen: '0px' },
  'border-bottom-right-radius': { deleteWhen: '0px' },
  'border-bottom-left-radius':  { deleteWhen: '0px' },
};

// Static shorthands the computed longhands displace. When the overlay writes
// any longhand of a family, the family's shorthand keys are deleted from the
// static bag — otherwise the runtime cascade could re-order them and the
// stale pre-mutation shorthand would win over the baked longhand. Documented
// loss: a `background` shorthand's non-color legs (image/position/…) are
// dropped; acceptable for the wall corpus (plain-color pass/fail ink) and
// visible in the fixture diff, not silent.
export const SHORTHAND_CONFLICTS = [
  'background', 'margin', 'padding', 'inset',
  'border', 'border-width', 'border-style', 'border-color',
  'border-top', 'border-right', 'border-bottom', 'border-left',
  'overflow',
  // wave-24 B-RC4: `list-style` expands to type + position + IMAGE. The
  // overlay now bakes the first two, so a surviving shorthand could
  // re-assert the stale pre-mutation position over them in the runtime
  // cascade — the exact failure the strip exists to prevent. Documented
  // loss, same class as `background`'s non-color legs: the list-style-IMAGE
  // leg is dropped (Chromium resolves it to an absolute file:// URL that
  // would defeat extract-fixture's asset inlining, so snapshotting it is
  // not an option). MEASURED scope of that loss on the pinned corpus: of
  // the 4,593 wall-tagged (post-load-eligible) tests, exactly 2 use the
  // `list-style` shorthand at all (css-contain/contain-style-dynamic-002
  // `inside decimal`, css-view-transitions/…/implicit-stacking-context
  // `none`) and NEITHER carries an image leg — both are strictly better
  // off with the baked longhands.
  'list-style',
  // wave-25 BD-RC4: `border-radius` expands to exactly the four corner
  // longhands the overlay now bakes — nothing is lost by stripping it, and
  // leaving it would let the stale pre-mutation `border-radius: 75px` win
  // over the baked `150px` corners in the runtime cascade (the precise
  // failure mode backdrop-filter-border-radius-change exhibits). NOT covered
  // by the existing 'border' entry: the `border` shorthand does not include
  // radius (CSS Backgrounds 3 §4.5), so the strip has to be named.
  'border-radius',
];

// ── Top-layer decline (static source scan) ───────────────────────────────────
//
// The top-layer promotion APIs — the same atoms wpt-not-applicable.mjs's
// Rule 4 wave-15 widening matches. Their rendered output (::backdrop boxes,
// top-layer stacking, overlay-transition retention AFTER hidePopover()) is
// not representable as per-element computed properties, so post-load mode
// declines these before ever launching a browser. Source scan (not a
// runtime :popover-open probe) on purpose: an element mid overlay-hide
// transition is STILL rendered in the top layer while no longer matching
// :popover-open — the probe would extract a fixture that misses the whole
// signal and then dishonestly re-score it.
export const TOP_LAYER_API_RX =
  /\.(?:showPopover|hidePopover|togglePopover|showModal|requestFullscreen)\s*\(/;

/** Reason string when post-load mode must decline this test outright, else
 *  null. Currently the only decline class is top-layer promotion (scope
 *  boundary in the header); new classes must add themselves HERE so the
 *  decline stays a single reviewable list. */
export function postLoadDecline(html) {
  // Top-layer APIs anywhere in source (script bodies) — see TOP_LAYER_API_RX.
  if (TOP_LAYER_API_RX.test(html)) return 'top-layer-undeliverable';
  return null;
}

// ── Wall-tag activation lookup ───────────────────────────────────────────────
//
// wpt-buckets.json's notApplicable map is the tag source of truth (stamped
// by bucket-wpt.mjs from wpt-not-applicable.mjs's rules). Lazy-loaded and
// cached like extract-fixture's bucketIndex; absent file ⇒ empty map so unit
// tests can run without the index.
let _naIdx = null;
async function notApplicableIndex() {
  if (_naIdx) return _naIdx;                        // cached after first read
  const p = join(REPO_ROOT, 'tools', 'titan', 'wpt-buckets.json');
  try {
    _naIdx = JSON.parse(await fs.readFile(p, 'utf8')).notApplicable ?? {};
  } catch {
    _naIdx = {};                                    // optional in unit tests
  }
  return _naIdx;
}

/** Pure predicate: does this tag list cross the extraction wall? Exported so
 *  extract-fixture's CLI and the tests share ONE activation rule. */
export function hasWallTag(naTags) {
  // Non-arrays (missing entries) carry no tags — not wall-tagged.
  return Array.isArray(naTags) && naTags.some((t) => EXTRACTION_WALL_TAGS.has(t));
}

/** Async convenience: wall-tag check for a repo-relative test path. */
export async function isWallTagged(testRel) {
  // Look the test up in the shared notApplicable map (see notApplicableIndex).
  return hasWallTag((await notApplicableIndex())[testRel]);
}

// ── Traversal identity: static side ──────────────────────────────────────────

/**
 * Flatten extractBodyTreeNested's nested tree into the pre-order list of
 * `{ path, tag }` entries, where `path` is the per-level kept-child index
 * chain. This is EXACTLY the identity buildComponents assigns ids from:
 * component `<stem>__2__0__1` ⇔ path [2, 0, 1] (buildComponents:
 * `tree.forEach((node, idx) => …)` at the top level, `children.forEach(
 * (child, i) => childId = `${id}__${i}`)` below), so a path maps 1:1 onto a
 * component without string parsing. The synthetic `__body`/`__text`
 * components have no tree node and therefore no path — they keep the static
 * path by construction. Exported for the mapping unit pins.
 */
export function flattenStaticPaths(tree) {
  const out = [];
  // Pre-order walk mirrors buildComponents' emission order exactly.
  const recurse = (nodes, prefix) => {
    nodes.forEach((node, i) => {
      const path = [...prefix, i];                  // this node's id path
      out.push({ path, tag: node.tag });            // record identity
      recurse(node.children ?? [], path);           // then its descendants
    });
  };
  recurse(tree, []);
  return out;
}

/**
 * Build the static traversal identity for a test's HTML: same comment strip,
 * same stylesheet assembly, same styledTags merge guard, same maxDepth-5
 * nested walk that extractFixture → buildComponents uses. `readLinkedCss`
 * mirrors extractFixture's linked-stylesheet loop (http(s) skipped, missing
 * files tolerated) because styledTags — and therefore the inline-merge
 * filter — must see the SAME rule set the fixture was built from.
 */
export async function staticPathsForHtml(html, testAbs) {
  const cleaned = stripComments(html);              // same pre-pass as extractFixture
  let allCss = extractInlineStyle(cleaned);         // inline <style> blocks
  for (const href of extractLinkedStylesheets(cleaned)) {
    if (/^https?:\/\//i.test(href)) continue;       // remote — bucketer filtered
    const cssAbs = href.startsWith('/')
      ? join(WPT_DIR, href.slice(1))                // corpus-absolute href
      : resolve(dirname(testAbs), href);            // test-relative href
    try {
      allCss += '\n' + stripComments(await fs.readFile(cssAbs, 'utf8'));
    } catch { /* missing linked CSS tolerated — mirrors extractFixture */ }
  }
  const rules = parseCss(allCss);                   // the fixture's rule set
  // The SAME mergeCtx filter buildComponents applies (styledTags guard);
  // resolveWhiteSpace only affects text content, never the kept-child
  // filter, so omitting it cannot change traversal identity.
  const tree = extractBodyTreeNested(cleaned, 5, { styledTags: collectStyledTags(rules) });
  return { paths: flattenStaticPaths(tree), rules };
}

// ── Traversal identity: browser side ─────────────────────────────────────────
//
// The in-page walker. Runs inside page.evaluate — everything it needs
// arrives via the serialized params object; it must stay dependency-free.
// It replicates the static walk's THREE filters element-for-element:
//   1. HEAD_ONLY_TAGS skipped at every depth (extractBodyTreeNested's Bug-5
//      filter — <script>/<style> nested in body never become components);
//   2. pure-inline mergeable children skipped (isPureInlineMergeable's four
//      conditions, translated to DOM terms: merge-tag list, zero attributes,
//      no ELEMENT children — comments were stripped on the static side, and
//      DOM childElementCount ignores them symmetrically — and tag not
//      directly targeted by a rule), with the wave-12 depth-0 nuance: the
//      LEADING run is absorbed into `__text` (skip), later inline elements
//      keep the component path (keep once a non-mergeable sibling was kept);
//   3. depth capped at 5 (the extractBodyTreeNested default buildComponents
//      uses).
// Per kept element it snapshots the enumerated computed properties, the
// bounding rect (cross-check + stability input), and the element scroll
// offsets (scroll guard input).
function inPageWalker(params) {
  const { headOnly, inlineMerge, styledTags, maxDepth, propNames } = params;
  const records = [];
  const walk = (parent, prefix, depth) => {
    let kept = 0;          // index among KEPT children — the id/path basis
    let seenBlock = false; // wave-12 depth-0 leading-run marker
    for (const el of parent.children) {
      const tag = el.tagName.toLowerCase();
      // Filter 1: head-only scaffolding never becomes a component.
      if (headOnly.includes(tag)) continue;
      // Filter 2: pure-inline mergeable — the DOM translation of
      // isPureInlineMergeable (see the walker banner for the mapping).
      const mergeable = inlineMerge.includes(tag)
        && el.attributes.length === 0
        && el.childElementCount === 0
        && !styledTags.includes(tag);
      if (mergeable) {
        // Depth ≥ 1: absorbed into the parent's ownText — never a component.
        // Depth 0: absorbed into __text only while in the LEADING run.
        if (depth !== 0 || !seenBlock) continue;
      } else {
        seenBlock = true; // a kept non-mergeable ends the leading run
      }
      const path = prefix.concat([kept]);
      kept += 1;
      // The enumerated computed snapshot — resolved/used values from the
      // live post-script layout.
      const cs = getComputedStyle(el);
      const styles = {};
      for (const name of propNames) styles[name] = cs.getPropertyValue(name);
      // Rect cross-check: rounded to 2 dp so the stability comparison isn't
      // defeated by float formatting while still catching any real movement.
      const r = el.getBoundingClientRect();
      // wave-29 ANCHOR delivery probe. The whole bake rests on "for an
      // out-of-flow box, the CSSOM resolved inset IS the used inset". That
      // premise is FALSE for anchor-aligned boxes: MEASURED in the pinned
      // headless Chromium on css-anchor-position/anchor-center-overflow-001,
      // an `.anchored { inset: 6px; place-self: anchor-center }` box reports
      // resolved left "6px" while it PAINTS at 24px from its containing
      // block's padding edge — the anchor-center alignment shift is applied
      // during layout and never folded back into the serialized value. Baking
      // the resolved string would position the box 18px wrong and then stamp
      // the fixture "delivered". So measure the discrepancy here, where
      // offsetParent is reachable, and let the caller bail on it.
      //
      // Scoped to anchor-flavoured alignment ONLY (not every out-of-flow
      // box): `margin:auto` centering and plain `align-self:center` on
      // abspos produce the same kind of delta, and those populations have
      // been delivering since wave-16 — widening this probe to them would
      // retro-bail shipped work on an untested premise.
      let anchorInsetDelta = null;
      const oof = cs.position === 'absolute' || cs.position === 'fixed';
      const anchorAligned = /anchor/i.test(cs.alignSelf) || /anchor/i.test(cs.justifySelf);
      if (oof && anchorAligned && cs.top !== 'auto' && cs.left !== 'auto') {
        // Containing block padding edge — what top/left are measured from.
        // offsetParent is null for fixed-position boxes (and for boxes in a
        // display:none subtree), whose CB is the initial containing block:
        // its padding edge is the viewport origin, so 0/0 is correct.
        const op = el.offsetParent;
        let cbTop = 0, cbLeft = 0;
        if (op) {
          const orr = op.getBoundingClientRect();
          const ocs = getComputedStyle(op);
          cbTop  = orr.top  + parseFloat(ocs.borderTopWidth);
          cbLeft = orr.left + parseFloat(ocs.borderLeftWidth);
        }
        // The rect is the BORDER box; `top`/`left` position the MARGIN box.
        const paintedTop  = r.top  - cbTop  - parseFloat(cs.marginTop);
        const paintedLeft = r.left - cbLeft - parseFloat(cs.marginLeft);
        anchorInsetDelta = [
          +(parseFloat(cs.top)  - paintedTop).toFixed(2),
          +(parseFloat(cs.left) - paintedLeft).toFixed(2),
        ];
      }
      records.push({
        path, tag, styles,
        rect: { x: +r.x.toFixed(2), y: +r.y.toFixed(2),
                width: +r.width.toFixed(2), height: +r.height.toFixed(2) },
        // Scroll guard inputs — the IR has no scroll-offset model.
        scrollTop: el.scrollTop, scrollLeft: el.scrollLeft,
        // null on every non-anchor-aligned element (the whole pre-wave-29
        // corpus), so the settle-stability JSON comparison is unaffected.
        anchorInsetDelta,
      });
      // Filter 3: bounded recursion, same cap as the static walk.
      if (depth + 1 < maxDepth) walk(el, path, depth + 1);
    }
  };
  walk(document.body, [], 0);
  return {
    records,
    // Document-level scroll — a scrolled viewport is equally undeliverable.
    docScrollTop:  document.scrollingElement ? document.scrollingElement.scrollTop  : 0,
    docScrollLeft: document.scrollingElement ? document.scrollingElement.scrollLeft : 0,
  };
}

// ── Cross-checks (pure, exported for unit pins) ──────────────────────────────

/** Settle-stability: two snapshots taken 100 ms apart must be IDENTICAL —
 *  any drift means a timer / rAF loop / running transition is still
 *  animating box state, i.e. the test is not time-stable and stays on the
 *  static path (scope boundary in the header). Deep equality via JSON: both
 *  snapshots are plain data produced by the same walker in the same order,
 *  so serialization is deterministic. */
export function snapshotsStable(a, b) {
  return JSON.stringify(a) === JSON.stringify(b);
}

/** wave-29 ANCHOR delivery cross-check (pure — exported for unit pins).
 *
 *  Reads the per-record `anchorInsetDelta` the walker measured (see its
 *  comment for the measurement and why it is scoped to anchor-aligned
 *  out-of-flow boxes) and answers ONE question: would baking the resolved
 *  insets put every anchored box where the browser actually paints it?
 *
 *  This is the honesty valve for the wave-29 wall extension. Admitting
 *  `requires-anchor-positioning-runtime` to EXTRACTION_WALL_TAGS makes the
 *  anchor population post-load ELIGIBLE, and a successful bake stamps
 *  `postLoadExtracted` — which re-admits the test to scoring on the claim
 *  that the input was delivered. Without this guard that claim would be
 *  false for the anchor-center-overflow family (MEASURED on 001 and 004: 36
 *  of the 96 anchored boxes in the raw DOM — 36 of the 48 that survive the
 *  walker's depth cap to become components — off by up to 50px) and the
 *  re-admitted tests would be scored as
 *  renderer failures for geometry the harness got wrong. Bailing keeps the
 *  wall exclusion, which is the honest outcome: not delivered, not scored.
 *
 *  Tolerance is half a device pixel — the same sub-pixel bar the rest of
 *  the pipeline uses; the walker already rounded to 2 dp.
 *
 *  @param {Array<object>} records walker records
 *  @param {number} [tol] max |resolved − painted| px before it is a mismatch
 *  @returns {string|null} null when deliverable, else a diagnostic string */
export function anchorInsetMismatch(records, tol = 0.5) {
  let probed = 0, bad = 0, worst = 0, worstPath = null;
  for (const r of records ?? []) {
    // null ⇒ not an anchor-aligned out-of-flow box; nothing to verify.
    if (!Array.isArray(r.anchorInsetDelta)) continue;
    probed++;
    // Worst axis decides — a box wrong on either axis is wrong.
    const d = Math.max(Math.abs(r.anchorInsetDelta[0]), Math.abs(r.anchorInsetDelta[1]));
    if (d > tol) bad++;
    if (d > worst) { worst = d; worstPath = (r.path ?? []).join('.'); }
  }
  // No anchored boxes probed, or every one lands where it resolves ⇒ the
  // resolved insets ARE the used/anchored offsets and the bake delivers.
  if (bad === 0) return null;
  return `${bad}/${probed} anchor-aligned boxes: resolved inset != painted offset ` +
         `(worst ${worst.toFixed(2)}px at path ${worstPath})`;
}

/** Element-mapping cross-check: the browser walk must agree with the static
 *  walk path-for-path AND tag-for-tag, in order. Any disagreement means the
 *  page's scripts mutated STRUCTURE (or the two walkers diverged on exotic
 *  markup) — either way the computed overlay would land on the wrong
 *  components, so the caller bails to the static path. Returns null when
 *  aligned, else a human-readable mismatch description (surfaced in logs). */
export function mappingMismatch(staticPaths, records) {
  // Count first: a script-inserted or -removed element shifts every later
  // sibling index, so a count mismatch is the common structural signal.
  if (staticPaths.length !== records.length) {
    return `element count: static ${staticPaths.length} vs browser ${records.length}`;
  }
  for (let i = 0; i < staticPaths.length; i++) {
    const s = staticPaths[i], b = records[i];
    const sKey = s.path.join('.'), bKey = b.path.join('.');
    // Same walk order ⇒ same path at the same rank; drift = structure moved.
    if (sKey !== bKey) return `path[${i}]: static ${sKey} vs browser ${bKey}`;
    // Same element kind at the same slot — tags are the identity cross-check.
    if (s.tag !== b.tag) return `tag@${sKey}: static <${s.tag}> vs browser <${b.tag}>`;
  }
  return null; // aligned — safe to overlay
}

// ── wave-20: post-load STRUCTURE extraction (the appendChild family) ────────
//
// The scope-guarded conversion of the element-mapping bail into a delivery
// path. Everything here is deliberately thin glue over extract-fixture.mjs —
// the serialized live DOM is just a different INPUT DOCUMENT for the exact
// static pipeline (extractFixture's htmlOverride), never a re-implementation.

// The one wall tag whose meaning IS structural mutation — the trigger scope.
// Scroll-only walls (requires-script-driven-scroll) and --force runs keep the
// wave-16 bail on mismatch: their drift is not the delivered capability.
export const STRUCTURE_TRIGGER_TAG = 'requires-script-mutation';

/** Pure trigger predicate: convert the mapping-mismatch bail into structure
 *  re-extraction ONLY when (a) the walks actually disagree (mismatch is a
 *  non-null description) AND (b) the test is wall-tagged with the structural
 *  mutation tag. Exported so the tests pin the scope guard exactly. */
export function shouldStructureExtract(naTags, mismatch) {
  // No drift → the cheaper state-bake path already handled the test.
  if (!mismatch) return false;
  // Drift outside the requires-script-mutation population → honest bail.
  return Array.isArray(naTags) && naTags.includes(STRUCTURE_TRIGGER_TAG);
}

// The id stamped on the injected canvas-frame <style> so the serializer can
// strip it — the canvas contract is the CAPTURE pipeline's own frame (every
// platform injects it at render time); baking it into the fixture would
// double-apply it.
export const CANVAS_FRAME_STYLE_ID = '__postLoadCanvasFrame';

// The HTML void elements (HTML §13.1.2) — tags whose HTML-namespace form
// never carries an end tag. Needed by the serialization normalizer below.
export const HTML_VOID_TAGS = [
  'area', 'base', 'br', 'col', 'embed', 'hr', 'img', 'input',
  'link', 'meta', 'param', 'source', 'track', 'wbr',
];

/** Normalize a serialized live-DOM fragment for the regex-based static
 *  parser. FOREIGN (non-HTML-namespace) elements — the createElementNS
 *  family, e.g. appearance-auto-non-html-namespace-001's
 *  `createElementNS('not-html', 'input')` — serialize WITH explicit end tags
 *  even for void-named tags (`<input></input>`), because the HTML
 *  serializer's void rule applies only to the HTML namespace. An HTML
 *  re-parse simply IGNORES those stray closers (the parser makes the tag
 *  void again), but the regex walker treats the unmatched `</input>` as a
 *  structure break and silently DROPS every following sibling (measured:
 *  static walk 4 vs live walk 8 on that test). Stripping the closers is
 *  semantics-preserving under the HTML re-parse — HTML-namespace void
 *  elements never serialize a closer, so only foreign ones can match. A
 *  foreign void-named element WITH children still cannot round-trip through
 *  an HTML parse (its children become siblings) — that case lands in the
 *  structure-remap-mismatch bail, honestly. Exported for the unit pins. */
export function stripForeignVoidClosers(html) {
  // One alternation over the void set, case-insensitive, global — closers
  // only (`</tag >`); open tags are untouched.
  return html.replace(new RegExp(`</(?:${HTML_VOID_TAGS.join('|')})\\s*>`, 'gi'), '');
}

/** Pure assembly of the synthetic document the static pipeline re-parses:
 *  the ORIGINAL head (rel=match link, fuzzy meta, <style>/<link> sheets —
 *  everything extractFixture reads from the head) around the SERIALIZED
 *  post-script body. Explicit <html>/<head>/<body> tags on purpose: the
 *  live parser already normalized implicit-body markup, so the serialized
 *  form always takes extractBody*'s explicit-body branch — no fallback
 *  heuristics involved. Exported for the serialized-DOM-input pins. */
export function buildSyntheticHtml(headInner, bodyOuter) {
  // bodyOuter is `<body …>…</body>` from the live DOM (outerHTML), so it
  // brings its own body tags; we only wrap head + html around it.
  return `<!DOCTYPE html>\n<html>\n<head>${headInner}</head>\n${bodyOuter}\n</html>`;
}

// wave-20 W1 note — widget attrs ride the serialization round-trip for free:
// outerHTML preserves source ATTRIBUTES (boolean ones serialize as
// `checked=""` — still presence, still `true` on the wire), so the
// re-extraction below emits `_attrs` for script-created widgets exactly as
// the static pipeline would. Documented loss, not silent: scripted PROPERTY
// mutations that HTML does not reflect back to attributes (`el.value = 'x'`,
// `el.checked = true` via IDL) do not serialize and therefore do not reach
// `_attrs` — same boundary the browser's own outerHTML draws.
//
// wave-20 fix 5 — NAMESPACES do NOT ride the round-trip: outerHTML flattens
// a createElementNS('not-html', 'input') element into plain `<input>` text,
// and the HTML re-parse puts it back in the HTML namespace — the risk this
// file's structure-path header always carried. Because the serializer still
// holds the LIVE DOM (where el.namespaceURI is authoritative), it stamps
// every non-XHTML element with extract-fixture's FOREIGN_NS_MARKER_ATTR on
// the CLONE before serializing; buildNode() then suppresses `_tag`/`_attrs`
// for marked elements, so a foreign 'input' never acquires widget identity
// (browsers paint NO chrome for it — appearance-auto-non-html-namespace-001
// renders six empty 1em inline-blocks, not six UA widgets).
//
// In-page serializer — runs inside page.evaluate (dependency-free, params
// serialized). Clones head and body so the LIVE DOM is untouched (the second
// walk still runs against it afterwards), then strips:
//   - the injected canvas-frame style (by CANVAS_FRAME_STYLE_ID — see above);
//   - every <script> element: they already RAN (their effect is the DOM
//     being serialized), and raw script text with `<`/`>` operators would
//     needlessly stress the regex-based static parser.
function inPageSerializer(params) {
  const { canvasStyleId, foreignNsMarker } = params;
  const XHTML_NS = 'http://www.w3.org/1999/xhtml';
  const head = document.head.cloneNode(true);   // deep clone — live head kept
  const injected = head.querySelector('#' + canvasStyleId);
  if (injected) injected.remove();              // the capture-pipeline frame
  for (const s of head.querySelectorAll('script')) s.remove(); // ran already
  const body = document.body.cloneNode(true);   // deep clone — live body kept
  for (const s of body.querySelectorAll('script')) s.remove(); // ran already
  // fix 5: cloneNode preserves namespaces even though outerHTML won't —
  // mark every foreign element NOW, while the truth is still queryable.
  // getElementsByTagName('*') enumerates all descendants of the detached
  // clone; setAttribute on a foreign element is legal (null-namespace
  // attribute) and serializes as ordinary text the regex walker scans.
  for (const el of body.getElementsByTagName('*')) {
    if (el.namespaceURI !== XHTML_NS) el.setAttribute(foreignNsMarker, '');
  }
  return { headInner: head.innerHTML, bodyOuter: body.outerHTML };
}

/** Adopt a re-extracted fixture's content into the ORIGINAL fixture object
 *  in place (callers hold the reference: extract-fixture's CLI writes the
 *  object it passed in). The re-extracted `_wpt` block is authoritative —
 *  same head, so test/ref/bucket/fuzzy are identical, while lossy/
 *  lossyReasons honestly reflect the POST-script tree. Both honesty stamps
 *  are applied here: `postLoadExtracted` (the wave-16 delivery record the
 *  score gate re-admits on) and `structureExtracted` (the wave-20 record
 *  that the component TREE itself is post-script). Exported for unit pins. */
export function adoptReExtractedFixture(fixture, reFixture) {
  fixture._wpt = reFixture._wpt;                // post-script provenance block
  fixture.components = reFixture.components;    // post-script component tree
  fixture._wpt.postLoadExtracted = true;        // state delivered (gate key)
  fixture._wpt.structureExtracted = true;       // structure delivered (gate key)
  return fixture;
}

/**
 * The structure-delivery path, called from postLoadAugmentFixture when
 * shouldStructureExtract fires. Order is the constraint the header states:
 * serialize structure → re-extract through the SAME pipeline → SECOND
 * traversal walk (static side = the serialized DOM, so it matches the live
 * DOM by construction) → adopt + state-bake. The passed fixture is only
 * mutated AFTER every cross-check passed — a bail here leaves it
 * byte-identical (same contract as the wave-16 bails).
 */
async function structureExtractFromLivePage(page, fixture, testRel, testAbs, stem) {
  // 1. Serialize the settled post-script DOM (canvas frame + scripts out),
  //    then normalize foreign void closers for the regex parser (see
  //    stripForeignVoidClosers — the createElementNS family's round-trip).
  const { headInner, bodyOuter } =
    await page.evaluate(inPageSerializer, {
      canvasStyleId: CANVAS_FRAME_STYLE_ID,
      // fix 5: the marker the serializer stamps on non-XHTML elements so
      // widget identity never survives the namespace-losing re-parse.
      foreignNsMarker: FOREIGN_NS_MARKER_ATTR,
    });
  const syntheticHtml = buildSyntheticHtml(headInner, stripForeignVoidClosers(bodyOuter));
  // 2. Re-run the FULL static extraction on the serialized document —
  //    extractFixture's htmlOverride swaps only the input source; stylesheet
  //    resolution / ref lookup / asset inlining all run against testAbs's
  //    directory exactly as the static pass did.
  const reResult = await extractFixture(testRel, { htmlOverride: syntheticHtml });
  // 3. SECOND traversal identity, computed FROM the serialized document, so
  //    the state bake maps onto the re-extracted components. styledTags come
  //    from the synthetic document's OWN rule set (staticPathsForHtml returns
  //    it) so both sides of this mapping share one merge guard — including
  //    any <style> a script inserted into the body.
  const { paths: newPaths, rules: newRules } = await staticPathsForHtml(syntheticHtml, testAbs);
  const snap3 = await page.evaluate(inPageWalker, {
    headOnly:    [...HEAD_ONLY_TAGS],
    inlineMerge: [...INLINE_MERGE_TAGS],
    styledTags:  [...collectStyledTags(newRules)],
    maxDepth: 5,
    propNames: POST_LOAD_COMPUTED_PROPERTIES,
  });
  // 4. Defensive remap check: serialized-DOM walk vs live walk SHOULD agree
  //    by construction — any residual drift (regex-parser blind spot on
  //    exotic serialized markup) must bail with the fixture untouched, never
  //    overlay onto wrong components.
  const remap = mappingMismatch(newPaths, snap3.records);
  if (remap) return { status: 'bailed', reason: `structure-remap-mismatch (${remap})` };
  // 4b. wave-29 anchor guard, applied to the RE-WALKED records too: the
  //     structure path bakes snap3, so it owes the same "the insets we bake
  //     are the anchored offsets" promise the overlay path makes. A test can
  //     reach here carrying both wall tags (script mutation AND anchor
  //     positioning), and stamping it delivered on pre-alignment insets
  //     would be the same dishonesty by a different route.
  const anchorGap3 = anchorInsetMismatch(snap3.records);
  if (anchorGap3) {
    return { status: 'bailed', reason: `anchor-inset-undeliverable (${anchorGap3})` };
  }
  // 5. Adopt the post-script tree, then bake the post-script state onto it —
  //    both structure AND state are now from the same settled live page.
  adoptReExtractedFixture(fixture, reResult.fixture);
  const overlaid = mergePostLoadIntoFixture(fixture, stem, snap3.records);
  return { status: 'extracted', structure: true, overlaid,
           elements: newPaths.length, records: snap3.records };
}

// ── wave-29 S-RC5: the POST-LOAD PSEUDO BAG ─────────────────────────────────
//
// THE HOLE. overlayComputedOnComponent's contract says `_text`, `_pseudo`,
// `_tag`, `_attrs`, `children` "keep the static path" — sound for the first
// four (they are structural identity or authored text, which a style mutation
// does not touch) but WRONG for `_pseudo`. A pseudo bag is not structure: it
// is the SELECTOR-MATCH RESULT of the `::before`/`::after`/`::marker` rules
// against the element's attributes, and the single most common thing a WPT
// dynamic reftest mutates is exactly those attributes.
//
// MEASURED (css-pseudo/before-dynamic-display-none, the VETO that opened
// this): the stylesheet is
//     #id::before      { content:"FAIL"; position:absolute; …; background:red }
//     #id.none::before { display: none }
// and the script is `id.offsetTop; id.className = "none"`. The mutation is
// PURELY a class change, so the element walk is structurally identical, the
// state bake takes the (cheap) overlay path, and no structure re-extraction
// ever runs. The overlay then rewrites `properties` from getComputedStyle
// while `_pseudo.before` keeps its STATIC value — the pre-mutation bag with
// `content: "FAIL"`, a 100x100 red absolute box. The fixture was stamped
// `postLoadExtracted: true` and re-admitted to scoring while still painting
// the exact FAIL box the test exists to prove is gone.
//
// THE FIX, and why it is this one. The class list only decides which rules
// match, so the honest re-derivation is to re-run the SELECTOR MATCH against
// the post-mutation DOM — and the pipeline already owns a component that does
// precisely that: the static extractor. The structure path (wave-20) proved
// the serialize → `extractFixture(htmlOverride)` round-trip, so the cheapest
// correct implementation reuses BOTH halves of it — the same inPageSerializer
// (so a pseudo re-derivation and a structure re-extraction can never disagree
// about what "the post-load DOM" is) and the same static builder (so the bag
// SHAPE — merge order, sibling-index bake, attr() bake, animation sampling,
// per-pseudo lossy markers — cannot drift from a second implementation).
// Re-implementing pseudo matching against a scraped class list would have
// forked all of that.
//
// SCOPE, three guards, in cost order:
//   1. Only post-load-extracted tests reach here at all (this module's own
//      activation gate). The pure static path never calls it.
//   2. `hasPseudoElementRules` — no `::before`/`::after`/`::marker` rule in
//      the source means no bag can exist on either side of the mutation, so
//      the extra page.evaluate + re-extraction are skipped entirely. This is
//      what keeps the cost off the ~99% of wall-tagged tests with no
//      generated content.
//   3. `pseudoRemapMismatch` — the two component ID SPACES must be identical
//      before anything is copied. They are by construction (mappingMismatch
//      already passed, so live structure == static structure == serialized
//      structure), so a mismatch means a regex-parser blind spot on exotic
//      serialized markup; it BAILS the whole post-load rather than copying
//      onto the wrong components. A bail leaves the fixture byte-identical,
//      the wall exclusion stands, and the test is honestly not scored.
//
// The structure path needs none of this: adoptReExtractedFixture already
// replaces the WHOLE component tree with the re-extracted one, so its pseudo
// bags are post-load by construction.

/** Does the source declare any pseudo-element rule the extractor can build a
 *  bag from? Matches the three names buildComponents supports (extract-fixture
 *  selectorMatchesPseudoElement: before / after / marker), in either the CSS3
 *  `::` or the CSS2 legacy `:` spelling. Comments are stripped first so a
 *  commented-out rule cannot cost a browser round-trip. Deliberately scans the
 *  WHOLE source, not just <style> blocks, so an inline `style=` attribute or
 *  an odd `<style>`-less spelling still arms it — this gate must err toward
 *  RUNNING (a false "no rules" silently restores the stale-bag bug).
 *
 *  SCOPE (skeptic-29 correction): the string it is handed is the only thing
 *  it can see. `<link rel=stylesheet>` sheets are NOT resolved here — the
 *  caller must append their text (linkedCssTextFor below) before asking.
 *  The original wave-29 comment claimed the re-extraction resolved them
 *  anyway; it does (extractFixture assembles inline + linked CSS), but this
 *  gate SHORT-CIRCUITS BEFORE that re-extraction ever runs, so a test whose
 *  only ::before rule lives in a linked sheet would have skipped the repair
 *  entirely. Measured corpus reach of that hole: 1 wall-tagged test
 *  (css-tables/tentative/table-height-redistribution, bucket B), so the fix
 *  is cheap insurance rather than a live regression. Exported for pins. */
export function hasPseudoElementRules(html) {
  return /::?(?:before|after|marker)\b/i.test(stripComments(String(html ?? '')));
}

/** Resolve a test's `<link rel=stylesheet>` hrefs to raw CSS text, mirroring
 *  extractFixture's own linked-stylesheet loop exactly (http(s) skipped
 *  because the bucketer already filtered remote deps; a missing file is
 *  tolerated, not fatal — some WPT tests link optional resources). Used ONLY
 *  to feed hasPseudoElementRules, so an unreadable sheet degrades to the
 *  pre-existing inline-only behaviour rather than failing the extraction.
 *  Exported for pins. */
export async function linkedCssTextFor(html, testAbs) {
  let out = '';
  for (const href of extractLinkedStylesheets(stripComments(String(html ?? '')))) {
    if (/^https?:\/\//i.test(href)) continue;         // remote — bucketer filtered
    const cssAbs = href.startsWith('/')
      ? join(WPT_DIR, href.slice(1))                  // corpus-absolute href
      : resolve(dirname(testAbs), href);              // test-relative href
    try { out += '\n' + await fs.readFile(cssAbs, 'utf8'); } catch { /* tolerated */ }
  }
  return out;
}

/** Flatten a fixture's component tree to `Map<id, cmp>`, descending the
 *  `children` maps. Used to compare two trees by ID SPACE and to copy the
 *  pseudo bags across. Exported for pins. */
export function flattenComponents(fixture) {
  const out = new Map();
  const visit = (map) => {
    for (const [id, cmp] of Object.entries(map ?? {})) {
      out.set(id, cmp);
      if (cmp && typeof cmp === 'object' && cmp.children) visit(cmp.children);
    }
  };
  visit(fixture?.components);
  return out;
}

/** Cross-check: do the ORIGINAL and RE-EXTRACTED trees address the same
 *  components? Returns a human-readable mismatch description, or null when
 *  the ID spaces are identical. Same defensive role mappingMismatch plays for
 *  the state bake — pseudo bags are keyed by component, so a divergent ID
 *  space means a copy would land generated content on the wrong box. */
export function pseudoRemapMismatch(originalIds, reIds) {
  if (originalIds.length !== reIds.length) {
    return `component count: static ${originalIds.length} vs post-load ${reIds.length}`;
  }
  // Both trees are produced by the same builder walking in document order,
  // so a positional compare is the strictest (and cheapest) identity check.
  for (let i = 0; i < originalIds.length; i++) {
    if (originalIds[i] !== reIds[i]) {
      return `id[${i}]: static ${originalIds[i]} vs post-load ${reIds[i]}`;
    }
  }
  return null;
}

/** Copy every re-derived `_pseudo` bag onto the original components, keyed by
 *  component id. SET and DELETE are both load-bearing: a mutation that ADDS a
 *  matching rule must add the bag, and — the before-dynamic-display-none
 *  shape — a mutation that changes which rules match must replace it. Nothing
 *  else on the component is touched: `properties` belongs to the computed
 *  overlay that runs next, and `_text`/`_tag`/`_attrs`/`children` really are
 *  structural identity the mutation did not change (mappingMismatch proved
 *  it). Returns the number of components whose bag actually changed, for the
 *  CLI log line. Exported for pins. */
export function applyPostLoadPseudoBags(original, reExtracted) {
  let changed = 0;
  for (const [id, cmp] of original) {
    const reCmp = reExtracted.get(id);
    const next = reCmp?._pseudo;
    const prev = cmp._pseudo;
    // Compare serialized form: the bags are plain JSON built by the same
    // builder in the same key order, so this is an exact value compare and
    // an unchanged bag leaves the object graph untouched.
    if (JSON.stringify(prev ?? null) === JSON.stringify(next ?? null)) continue;
    if (next === undefined) delete cmp._pseudo; else cmp._pseudo = next;
    changed++;
  }
  return changed;
}

/** Union the re-extracted fixture's lossy record into the original's.
 *  Direction is deliberate — reasons are ADDED, never subtracted. A reason
 *  that belonged to a pseudo bag the mutation removed cannot be attributed
 *  back (the static record is a flat set with no per-bag provenance), and
 *  over-reporting lossiness is the safe error: applyNaScoreGate consults
 *  lossyReasons only to CONFIRM a `requires-bundled-asset` exclusion, so an
 *  extra reason can never promote a test into the scored set, while a missing
 *  one could hide a real delivery gap. */
export function unionLossyRecord(fixture, reFixture) {
  const merged = new Set([
    ...(fixture._wpt?.lossyReasons ?? []),
    ...(reFixture._wpt?.lossyReasons ?? []),
  ]);
  fixture._wpt.lossyReasons = [...merged];
  fixture._wpt.lossy = fixture._wpt.lossy === true || reFixture._wpt?.lossy === true;
}

/**
 * Re-derive the `_pseudo` bags from the settled post-load DOM, for the
 * OVERLAY (non-structure) path. Returns `{ status: 'ok', changed }` or
 * `{ status: 'bailed', reason }`; on 'ok' the fixture's pseudo bags (and its
 * lossy record) have been updated in place, on 'bailed' it is untouched.
 * See the section banner for the full rationale and scope guards.
 */
async function rePseudoFromLivePage(page, fixture, testRel, html, testAbs) {
  // Guard 2 — nothing to re-derive without a pseudo-element rule anywhere.
  // skeptic-29: the gate must see the SAME sheets the fixture's bags were
  // built from, so linked stylesheets are appended before asking. The read
  // only happens when the inline source alone did not already arm the gate,
  // so the common case still costs zero IO.
  if (!hasPseudoElementRules(html)
      && !hasPseudoElementRules(await linkedCssTextFor(html, testAbs))) {
    return { status: 'ok', changed: 0, skipped: true };
  }
  // Serialize the settled DOM with the SAME serializer the structure path
  // uses (canvas frame + scripts stripped, foreign namespaces marked), then
  // the same void-closer normalization for the regex-based static parser.
  const { headInner, bodyOuter } = await page.evaluate(inPageSerializer, {
    canvasStyleId: CANVAS_FRAME_STYLE_ID,
    foreignNsMarker: FOREIGN_NS_MARKER_ATTR,
  });
  const syntheticHtml = buildSyntheticHtml(headInner, stripForeignVoidClosers(bodyOuter));
  // Re-run the FULL static extraction on the post-load document. Only its
  // `_pseudo` bags are consumed — `properties` are about to be overwritten by
  // the computed overlay, which is strictly better state than any re-parse.
  const reResult = await extractFixture(testRel, { htmlOverride: syntheticHtml });
  // Guard 3 — identical ID spaces, checked BEFORE any mutation.
  const original = flattenComponents(fixture);
  const reMap = flattenComponents(reResult.fixture);
  const remap = pseudoRemapMismatch([...original.keys()], [...reMap.keys()]);
  if (remap) return { status: 'bailed', reason: `pseudo-remap-mismatch (${remap})` };
  const changed = applyPostLoadPseudoBags(original, reMap);
  // Only touch the lossy record when a bag actually moved — a no-op
  // re-derivation must leave the fixture byte-identical.
  if (changed > 0) unionLossyRecord(fixture, reResult.fixture);
  return { status: 'ok', changed };
}

// ── Merge: computed overlay onto the static fixture ──────────────────────────

/** Locate the component object for a walk path inside a fixture built by
 *  buildComponents. Top level: `components[`${stem}__${p0}`]`; deeper
 *  levels: the `children` map keyed `${parentId}__${i}` (the flat-children
 *  map-in shape the Kotlin parser expects). Returns null when absent —
 *  callers treat that as a mapping failure (cannot happen when
 *  mappingMismatch passed, since both derive from the same tree). */
export function componentAtPath(fixture, stem, path) {
  // Top-level id is `${stem}__${first index}`.
  let id = `${stem}__${path[0]}`;
  let cmp = fixture.components?.[id];
  // Each further index descends one children level, extending the id.
  for (let i = 1; cmp && i < path.length; i++) {
    id = `${id}__${path[i]}`;
    cmp = cmp.children?.[id];
  }
  return cmp ?? null;
}

/**
 * Overlay one element's computed snapshot onto its component's properties.
 * Contract (unit-pinned):
 *   - conflicting static shorthands are DELETED first (SHORTHAND_CONFLICTS)
 *     so a stale pre-mutation `background`/`border` can never outrank the
 *     baked longhands in the runtime cascade;
 *   - every enumerated property is written with its computed value, EXCEPT
 *     the WRITE_RULES defaults ('auto' insets / z-index, 'none' transform,
 *     non-px width/height) which are delete-not-write: the static key is
 *     removed (a stale value must not linger) but no declaration is added;
 *   - the computed `box-sizing` is baked alongside width/height because gCS
 *     expresses those sizes in the element's OWN basis (border-box elements
 *     report border-box px — the block-axis-constraint parents are exactly
 *     this shape); a mismatched basis would re-interpret the baked number;
 *   - `_text`, `_pseudo`, `_tag`, `_attrs`, `children` are untouched HERE —
 *     this function only ever writes into `properties` (wave-20 W1: `_attrs`
 *     joined the list — widget identity is structural, not computed state).
 *     wave-29 S-RC5 note: `_pseudo` is still untouched by THIS function, but
 *     it is no longer "static-only content" — rePseudoFromLivePage re-derives
 *     the bags from the settled DOM before the overlay runs. The division of
 *     labour is deliberate: computed style is a per-PROPERTY overlay, a
 *     pseudo bag is a whole selector-match result that only the static
 *     builder can rebuild.
 */
export function overlayComputedOnComponent(cmp, styles, opts = {}) {
  const props = cmp.properties ?? (cmp.properties = {});
  // Wave 22 — `onlyMissing` is the collapsed-wrapper fold mode (see
  // mergePostLoadIntoFixture): an absorbed element's record must never
  // overwrite state the absorbing component already carries, and it must
  // not strip the ancestor's shorthands either (the ancestor's OWN record
  // handles its own conflicts in normal overlay mode).
  if (!opts.onlyMissing) {
    // Shorthand strip — see the SHORTHAND_CONFLICTS doc for the loss note.
    for (const sh of SHORTHAND_CONFLICTS) delete props[sh];
  }
  // Enumerated overlay with delete-not-write defaults.
  for (const name of POST_LOAD_COMPUTED_PROPERTIES) {
    // Fold mode: existing keys win — the absorbing component's authored
    // + own-record state has precedence over an absorbed wrapper's.
    if (opts.onlyMissing && props[name] !== undefined) continue;
    const v = styles[name];
    const rule = WRITE_RULES[name];
    // Missing value (defensive — walker always supplies all names): skip.
    if (v === undefined || v === null || v === '') continue;
    // deleteWhen: the computed default carries no declaration.
    if (rule?.deleteWhen !== undefined && v === rule.deleteWhen) { delete props[name]; continue; }
    // requirePx: keyword sizes ('auto', 'fit-content(…)') are not concrete
    // used values — delete the stale static key instead of writing them.
    if (rule?.requirePx && !/^-?\d+(?:\.\d+)?px$/.test(v)) { delete props[name]; continue; }
    props[name] = v; // the baked post-script value
  }
  // Over-constraint drop: Chromium's resolved values populate ALL FOUR
  // insets for out-of-flow boxes (left+right+width simultaneously). CSS2
  // §10.3.7 resolves that over-constraint by IGNORING the trailing inset
  // (`right` in LTR; §10.6.4 ignores `bottom` for the block axis) — the
  // browser-side numbers are consistent only under that rule, and a native
  // applier that honours both edges would double-place the box. Keep the
  // leading inset + the concrete size, drop the redundant trailing inset.
  // When the leading inset was NOT written (auto → deleted above), the
  // trailing one is the real anchor and stays.
  if (props.left !== undefined && props.width !== undefined) delete props.right;
  if (props.top !== undefined && props.height !== undefined) delete props.bottom;
}

/**
 * Merge a full walk snapshot into the fixture: overlay every mapped
 * component, then stamp `_wpt.postLoadExtracted: true` — the delivery
 * record applyNaScoreGate consults (via build-combined-fixture's keyMap).
 * The notApplicable tag itself is deliberately NOT touched anywhere: it
 * stays in wpt-buckets.json as provenance of WHY post-load ran.
 */
export function mergePostLoadIntoFixture(fixture, stem, records) {
  let overlaid = 0; // how many components actually received computed state
  for (const rec of records) {
    let cmp = componentAtPath(fixture, stem, rec.path);
    // Wave 22 — the inline-chain collapse (extract-fixture collapseInlineRun)
    // absorbs pure decoration wrappers (<s>/<u>/… ) into their parent's
    // text run, so the live DOM's element walk has MORE nodes than the
    // fixture tree at exactly those paths. When the record's own path has
    // no component but the NEAREST MAPPED ANCESTOR carries the collapse
    // marker (`_decorations`), the element was absorbed by design: fold
    // its computed state into that ancestor with ancestor-wins precedence
    // (the ancestor's own record overlays LATER in walk order and would
    // overwrite conflicts anyway — but we guard explicitly by only
    // writing keys the ancestor does not already carry from ITS record).
    // This is honest, not a silent skip: the wrapper's post-script state
    // that MATTERS (decoration color via currentColor, the recalc-002
    // shape) is exactly the state the collapse hoisted onto the parent.
    if (!cmp) {
      for (let cut = rec.path.length - 1; cut >= 1 && !cmp; cut--) {
        const anc = componentAtPath(fixture, stem, rec.path.slice(0, cut));
        if (anc && anc._decorations) cmp = anc;
      }
      if (cmp) {
        overlayComputedOnComponent(cmp, rec.styles, { onlyMissing: true });
        overlaid++;
        continue;
      }
      // No collapse ancestor either — mappingMismatch() ran before merge,
      // so this is a programming error: fail LOUDLY rather than silently
      // under-overlaying.
      throw new Error(`post-load merge: no component at path ${rec.path.join('.')}`);
    }
    overlayComputedOnComponent(cmp, rec.styles);
    overlaid++;
  }
  // The delivery stamp (gate contract; see inject-wpt-block.applyNaScoreGate).
  fixture._wpt.postLoadExtracted = true;
  return overlaid;
}

// ── Browser plumbing ─────────────────────────────────────────────────────────

// One shared browser per process (the proving set is 12 page loads — a
// launch per test would dominate wall time). Lazily created; callers MUST
// closePostLoadBrowser() before exit (CLI + extract-fixture's hook do).
let _browser = null;
async function getBrowser() {
  if (_browser) return _browser;                    // reuse across tests
  _browser = await puppeteer.launch({
    headless: 'new',                                // same mode as browser-ref
    args: BROWSER_LAUNCH_ARGS,                      // the shared flag contract
    protocolTimeout: 300_000,                       // long-run safety margin
  });
  return _browser;
}

/** Close the shared browser (no-op when never launched). */
export async function closePostLoadBrowser() {
  if (_browser) { await _browser.close(); _browser = null; }
}

/**
 * Run post-load extraction for one test and (on success) bake the result
 * into `fixture` in place. Returns `{ status, reason?, records? }` where
 * status ∈ 'extracted' | 'declined' | 'bailed':
 *   - 'declined' — static pre-check says the state is unrepresentable
 *     (top-layer); no browser was launched;
 *   - 'bailed'   — the live page failed a runtime cross-check (unstable /
 *     scrolled / structure drift outside the requires-script-mutation
 *     population); fixture left byte-identical, the wave-15 exclusion stays;
 *   - 'extracted' — computed state delivered, fixture stamped. When the
 *     wave-20 structure path ran, `structure: true` rides along and the
 *     fixture ALSO carries `_wpt.structureExtracted` (tree re-extracted from
 *     the serialized post-script DOM, then state-baked).
 */
export async function postLoadAugmentFixture(fixture, testRel) {
  const testAbs = join(WPT_DIR, testRel);
  const html = await fs.readFile(testAbs, 'utf8');
  // Static decline first — cheap, and avoids a browser launch for the
  // whole top-layer family.
  const decline = postLoadDecline(html);
  if (decline) return { status: 'declined', reason: decline };
  // Static traversal identity (paths + tags) for the mapping cross-check.
  const { paths: staticPaths } = await staticPathsForHtml(html, testAbs);
  // Live page under the browser-ref rendering contract.
  const browser = await getBrowser();
  const page = await browser.newPage();
  try {
    // Viewport BEFORE goto so the page's own onload layout reads (the
    // dynamic-change family forces layout mid-script) run at the pipeline's
    // canvas, not puppeteer's 800×600 default.
    // wave-25 round 3 (BAKE VIEWPORT ALIGNMENT): the pipeline canvas is the
    // ref's CONTENT space — 358×568, the exact viewport capture-browser-ref
    // renders at — NOT the 390×600 outer canvas. The 16px frame is applied
    // to the ref PNG in image space, so a page laid out at 390 with a 16px
    // body pad has the same content WIDTH but a 32px-larger ICB: `100vw`,
    // `100vh`, `min-height:100vh` and every ICB-relative computed inset came
    // back 32px too big and got baked into the fixture.
    await page.setViewport({
      width: REF_RENDER_WIDTH, height: REF_RENDER_MIN_HEIGHT, deviceScaleFactor: 1,
    });
    // file:// so relative resources resolve — same as capture-browser-ref.
    await page.goto('file://' + encodeURI(testAbs), { waitUntil: 'load', timeout: 30_000 });
    // The identical zero-specificity canvas frame the ref capture injects
    // (white canvas, pad, black ink, Inter faces, line-height pin) — see
    // capture-browser-ref.renderOne for the full per-declaration rationale.
    // wave-20: injected via evaluate (not addStyleTag) so the tag carries
    // CANVAS_FRAME_STYLE_ID — the structure serializer must strip it, since
    // the canvas frame belongs to the CAPTURE pipeline, never the fixture.
    await page.evaluate(({ id, css }) => {
      const s = document.createElement('style'); // same effect as addStyleTag
      s.id = id;                                 // …but identifiable later
      s.textContent = css;                       // the shared frame contract
      document.head.appendChild(s);              // head, like addStyleTag did
    }, {
      id: CANVAS_FRAME_STYLE_ID,
      // wave-25 round 3: the SHARED factory, not a copy. Identical bytes to
      // the sheet capture-browser-ref injects — zero body pad, flow-root
      // body, the v4.1 ink/font/line-height pins.
      css: await canvasFrameCss(),
    });
    // fonts.ready + double-rAF: the browser-ref settle lesson — the data-URI
    // Inter faces load async, and geometry snapshotted before the relayout
    // with the loaded face would wrap differently than the ref/harness.
    await page.evaluate(() => document.fonts.ready.then(
      () => new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(r))),
    ));
    // Walker params — the shared filter constants, serialized for the page.
    const params = {
      headOnly:   [...HEAD_ONLY_TAGS],
      inlineMerge: [...INLINE_MERGE_TAGS],
      // styledTags from the SAME rule set the static tree used — recompute
      // here so both sides of the mapping share one guard.
      styledTags: [...collectStyledTags(parseCss(extractInlineStyle(stripComments(html))))],
      maxDepth: 5,
      propNames: POST_LOAD_COMPUTED_PROPERTIES,
    };
    // Settle-stability: two snapshots 100 ms apart must be identical.
    const snap1 = await page.evaluate(inPageWalker, params);
    await new Promise((r) => setTimeout(r, 100));
    const snap2 = await page.evaluate(inPageWalker, params);
    // wave-20 mutation-settled check FIRST: an element-count drift between
    // the two snapshots means a timer/rAF loop is STILL creating or removing
    // elements — structure re-extraction would serialize a moving target, so
    // it bails with its own reason (more diagnostic than the generic
    // stability bail the same drift would also trip below).
    if (snap1.records.length !== snap2.records.length) {
      return { status: 'bailed', reason: 'mutation-not-settled' };
    }
    if (!snapshotsStable(snap1, snap2)) {
      return { status: 'bailed', reason: 'unstable-after-settle' };
    }
    // Scroll guard: any element-level or document-level offset means the
    // visual state depends on scroll positions the IR cannot carry.
    if (snap2.docScrollTop !== 0 || snap2.docScrollLeft !== 0 ||
        snap2.records.some((r) => r.scrollTop !== 0 || r.scrollLeft !== 0)) {
      return { status: 'bailed', reason: 'scroll-offset-undeliverable' };
    }
    // wave-29 anchor guard: the resolved insets we are about to bake must
    // BE the anchored offsets. Checked before the mapping cross-check
    // because it is cheaper and its bail is more diagnostic — a fixture that
    // maps perfectly but carries pre-alignment insets is the worse failure
    // (it would be stamped delivered and re-scored). See anchorInsetMismatch.
    const anchorGap = anchorInsetMismatch(snap2.records);
    if (anchorGap) {
      return { status: 'bailed', reason: `anchor-inset-undeliverable (${anchorGap})` };
    }
    // Element-mapping cross-check: static walk vs live walk.
    const mismatch = mappingMismatch(staticPaths, snap2.records);
    if (mismatch) {
      // wave-20 bail-to-trigger conversion: for the requires-script-mutation
      // population the drift IS the wall — deliver it by re-extracting the
      // fixture's STRUCTURE from the settled live DOM (see the structure
      // section above). Everything else keeps the wave-16 bail.
      if (shouldStructureExtract((await notApplicableIndex())[testRel], mismatch)) {
        // wave-21 collision fix: the stem MUST be the same subdir-encoded
        // fixtureStem() extractFixture seeded buildComponents' idPrefix
        // with — component ids in the fixture are `<stem>__N…`, so a bare
        // basename here would rebuild the structure under ids the fixture
        // doesn't contain for every nested test.
        const stem = fixtureStem(testRel);
        // `await` is load-bearing: a bare `return promise` inside this
        // try/finally would run the finally (page.close) BEFORE the structure
        // path finished evaluating against that very page.
        return await structureExtractFromLivePage(page, fixture, testRel, testAbs, stem);
      }
      return { status: 'bailed', reason: `element-mapping-mismatch (${mismatch})` };
    }
    // wave-29 S-RC5: re-derive the `_pseudo` bags from the settled DOM
    // BEFORE the overlay + stamp. Order matters twice over: (a) its bail
    // must leave the fixture byte-identical, which is only true while
    // mergePostLoadIntoFixture has not yet written the computed state or the
    // delivery stamp; (b) it is the last cross-check, so a fixture that
    // reaches the stamp has passed every "the thing we are about to call
    // delivered really is post-load" test — including generated content
    // (see the S-RC5 banner: a class-only mutation keeps the cheap overlay
    // path, so this is the ONLY place that repair can happen).
    const pseudo = await rePseudoFromLivePage(page, fixture, testRel, html, testAbs);
    if (pseudo.status === 'bailed') {
      return { status: 'bailed', reason: pseudo.reason };
    }
    // Delivered — overlay + stamp. wave-21 collision fix: componentAtPath
    // reconstructs ids as `<stem>__N…`, so the stem must be the SAME
    // subdir-encoded fixtureStem() the fixture's ids were built from.
    const stem = fixtureStem(testRel);
    const overlaid = mergePostLoadIntoFixture(fixture, stem, snap2.records);
    return { status: 'extracted', overlaid, records: snap2.records,
             // Surfaced in the CLI/extract-fixture log line so a bag repair
             // is visible in a batch run rather than silent.
             pseudoRederived: pseudo.changed };
  } finally {
    await page.close(); // one page per test; browser is shared
  }
}

// ── CLI ──────────────────────────────────────────────────────────────────────
//
// End-to-end per test: static extraction (extractFixture) → post-load
// augmentation → writeFixturePair. Declines/bails still WRITE the static
// pair (that IS the bail-to-static contract) and are reported per test.
async function main() {
  const inputs = process.argv.slice(2).filter((a) => a !== '--force');
  // --force runs tests that carry no wall tag too (debug aid); the default
  // activation is EXACTLY the wall-tagged population (header contract).
  const force = process.argv.includes('--force');
  if (inputs.length === 0) {
    console.error('usage: post-load-extract.mjs [--force] <relative-test-path>...');
    process.exit(1);
  }
  let hardFail = 0;
  const tally = { extracted: 0, declined: 0, bailed: 0, skipped: 0 };
  try {
    for (const rel of inputs) {
      try {
        // Activation gate: only wall-tagged tests get post-load treatment.
        if (!force && !(await isWallTagged(rel))) {
          console.log(`skip      ${rel} (no extraction-wall tag)`);
          tally.skipped++;
          continue;
        }
        const result = await extractFixture(rel);          // static pass
        const outcome = await postLoadAugmentFixture(result.fixture, rel);
        await writeFixturePair(result);                    // write either way
        tally[outcome.status]++;
        // wave-20: structure-path successes are visibly distinct in the log
        // (the fixture's component TREE was re-extracted, not just overlaid).
        console.log(`${outcome.status.padEnd(9)} ${rel}` +
          (outcome.reason ? ` (${outcome.reason})`
            : outcome.structure
              ? ` (structure re-extracted: ${outcome.elements} elements, ${outcome.overlaid} components overlaid)`
              // wave-29 S-RC5: a re-derived pseudo bag is a REPAIR (the
              // fixture was about to be stamped delivered with pre-mutation
              // generated content) — never let it land silently.
              : ` (${outcome.overlaid} components overlaid` +
                `${outcome.pseudoRederived ? `, ${outcome.pseudoRederived} pseudo bags re-derived` : ''})`));
      } catch (err) {
        hardFail++;
        console.error(`ERROR     ${rel}: ${err.message ?? err}`);
      }
    }
  } finally {
    await closePostLoadBrowser(); // never leak the shared browser
  }
  console.log(`post-load-extract: extracted=${tally.extracted} declined=${tally.declined} ` +
              `bailed=${tally.bailed} skipped=${tally.skipped} errors=${hardFail}`);
  process.exit(hardFail > 0 ? 1 : 0);
}

if (import.meta.url === `file://${process.argv[1]}`) {
  main().catch((err) => {
    console.error('post-load-extract: fatal:', err);
    process.exit(1);
  });
}
