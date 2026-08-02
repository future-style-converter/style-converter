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
//      (`_text`, `_pseudo`, the synthetic `__body`/`__text` components)
//      keeps the static path;
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
// 390-wide white canvas + pad, same Inter face embed + line-height pin —
// computed geometry must be measured under the environment the ref PNGs
// (and the harness captures) are produced in, or every overridden inset
// would carry a systematic offset (corpus-v4.1 font-pin lesson).
import {
  BROWSER_LAUNCH_ARGS, CANVAS_BG, CANVAS_PAD_PX,
  REF_FONT_STACK, REF_LINE_HEIGHT, interFontFaceCss,
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
      records.push({
        path, tag, styles,
        rect: { x: +r.x.toFixed(2), y: +r.y.toFixed(2),
                width: +r.width.toFixed(2), height: +r.height.toFixed(2) },
        // Scroll guard inputs — the IR has no scroll-offset model.
        scrollTop: el.scrollTop, scrollLeft: el.scrollLeft,
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
  // 5. Adopt the post-script tree, then bake the post-script state onto it —
  //    both structure AND state are now from the same settled live page.
  adoptReExtractedFixture(fixture, reResult.fixture);
  const overlaid = mergePostLoadIntoFixture(fixture, stem, snap3.records);
  return { status: 'extracted', structure: true, overlaid,
           elements: newPaths.length, records: snap3.records };
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
 *   - `_text`, `_pseudo`, `_tag`, `_attrs`, `children` are untouched —
 *     static-only content keeps the static path (wave-20 W1: `_attrs`
 *     joins the list — widget identity is structural, not computed state,
 *     and the overlay only ever writes into `properties`).
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
    // 390-wide canvas, not puppeteer's 800×600 default.
    await page.setViewport({ width: 390, height: 600, deviceScaleFactor: 1 });
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
      css: `
        ${await interFontFaceCss()}
        :where(html, body) { margin: 0; padding: 0; background: ${CANVAS_BG}; }
        :where(body) { padding: ${CANVAS_PAD_PX}px; box-sizing: border-box;
                       min-height: 100vh; color: #000;
                       font-family: ${REF_FONT_STACK};
                       line-height: ${REF_LINE_HEIGHT}; }
      `,
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
    // Delivered — overlay + stamp. wave-21 collision fix: componentAtPath
    // reconstructs ids as `<stem>__N…`, so the stem must be the SAME
    // subdir-encoded fixtureStem() the fixture's ids were built from.
    const stem = fixtureStem(testRel);
    const overlaid = mergePostLoadIntoFixture(fixture, stem, snap2.records);
    return { status: 'extracted', overlaid, records: snap2.records };
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
              : ` (${outcome.overlaid} components overlaid)`));
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
