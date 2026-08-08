/**
 * SDUI Component Renderer — the harness CALIBRATION SKIN (issue #41).
 *
 * The renderer CORE now lives in the package
 * (@style-converter/web/renderer — NodeRenderer/DocumentRenderer): slot
 * composition, text/pseudo rendering, sourceTag element mapping,
 * variables, the stylesheet class, all shared with real apps. THIS file
 * is only the capture calibration: every place screenshot comparability
 * needs non-CSS behaviour is expressed as an explicit RendererOptions
 * hook (HARNESS_OPTIONS below) instead of forked renderer code — one
 * renderer core, two skins.
 *
 * The five calibrations this skin adds (and the package default omits):
 *   1. display:none components render NOTHING (no capture canvas ever
 *      sees them) — the package keeps the box in the DOM, display:none.
 *   2. Sizing calibration (fit-content default, max-width:100% cap,
 *      50×30 px floors, aspect-ratio carve-outs, empty grid/flex → block)
 *      so a web <div> hugs content like SwiftUI/Compose intrinsic sizing
 *      — the package emits exactly what the engine produced.
 *   3. sourceTag ALLOWLIST (structural/inline tags only; interactive +
 *      replaced elements demote to <div>, except the img branch) so
 *      captures never pick up native control chrome — the package
 *      trusts the wire minus a document-breaking denylist.
 *   4. Placeholder label text for childless components (name +
 *      bg-luminance contrast colour, matching iOS/Android placeholders)
 *      — the package renders an empty element.
 *   5. The deterministic inline <img> placeholder src (fixed bytes →
 *      stable captures) — the package renders <img> without src until
 *      the wave-9 content contract carries one.
 *
 * Since the IR v2 freeze the wire is a flat component list; this
 * renderer receives a {@link ComposedNode} (built by Composer.ts from
 * `slot` refs — Mode A — or a root list for zero-slot Mode B docs) and
 * only ever hands ONE component's properties to the engine's
 * buildStyles. The engine never sees composition.
 */

import type { IRProperty } from '@style-converter/web/core/ir/IRModels';
import type { CSSStyles } from '@style-converter/web/core/renderer/StyleBuilder';
// Forced-state validation (spec 06 §6): the URL param must be one of the
// runtime-v1 conditions before the skin forwards it to the core.
import { isRuntimeV1Condition } from '@style-converter/web/core/renderer/RuleBuilder';
// The shared renderer core + its calibration-hook types (issue #41).
import { NodeRenderer } from '@style-converter/web/renderer/NodeRenderer';
import type { RenderContext, RendererOptions } from '@style-converter/web/renderer/RendererOptions';
// wave-20 W1: the package's widget tag set — the WPT-mode passthrough
// below must stay byte-parallel with the extractor's WIDGET_ATTR_TAGS
// and the core's attrs-application policy (WidgetAttrs.ts).
import { WIDGET_TAGS } from '@style-converter/web/renderer/WidgetAttrs';
// The cross-platform BLOCK FONT layout (atlas checksum cb3c6e411c7b2859):
// placeholder LABELS render as integer-coordinate 1x1 px rects instead of
// font-stack text, so all three platforms rasterize labels byte-identically
// (the fix for the glyph wall capping ~50 text-bearing fixtures at <0.95).
import {
  BLOCK_LABEL_FILL,
  BLOCK_LABEL_ORIGIN_X,
  BLOCK_LABEL_ORIGIN_Y,
  layoutBlockLabel,
} from '@style-converter/web/renderer/BlockFontLabel';
// Wave-19 lane FLOAT — the float-run segmentation twin (pins P1/P2/P6)
// behind the planChildRuns hook below (WPT capture only).
import {
  floatChildFacts,
  floatRunWrapperStyle,
  segmentFloatRuns,
} from '@style-converter/web/engine/layout/FloatRowPacking';
import type { ComposedNode } from './Composer';

/**
 * WPT-mode detector — reads the `?wpt=1` query parameter once per module load.
 *
 * Why this exists: TITAN's WPT capture pipeline (tools/titan/section-runner.sh)
 * extracts a one-component-per-test IR where the component name is the WPT
 * key `wpt__<section>__<stem>__<index>`. When the extracted IR is empty
 * (extractor dropped a body-level style, or the test renders nothing on a
 * 100×100 box) the only visible thing in the screenshot is the placeholder
 * text — `wpt css-backgrounds background-color-animation-in-body 0` etc. —
 * which the pilot agent (tools/titan/investigations/pilot-001/
 * css-backgrounds__background-color-animation-in-body.json) flagged as the
 * source of false `structural-divergence` labels across the entire WPT
 * corpus (the title overlay alone produces ~33 pHash hamming distance vs
 * the chromeless browser-ref).
 *
 * Suppress the placeholder text in WPT mode. Legacy 327-pair flow (no `?wpt=1`)
 * keeps the placeholder as before — it's load-bearing for the visual-test
 * fixture where empty containers need the name to be visually identifiable
 * against iOS / Android's PlaceholderLabel / PlaceholderContent.
 *
 * Read once at module-load time. URL params don't change during a capture
 * session (puppeteer navigates once); avoiding per-render `URLSearchParams`
 * construction keeps the hot render loop allocation-free.
 */
const WPT_MODE: boolean = (() => {
  if (typeof window === 'undefined') return false;
  return new URLSearchParams(window.location.search).get('wpt') === '1';
})();

/**
 * WPT COMPOSED-mode detector — reads `?wptComposed=1` once per module load.
 *
 * Why this exists (Round-4 TITAN GAP 1, height half): the composed capture
 * (ComposedCaptureGallery) renders each WPT test's components composed on ONE
 * ref-framed canvas, then diffs it against the Chromium browser-ref. The ref
 * renders the reference page's real DOM — a text `<p>` bar is exactly one
 * line-box tall (20px @16px since the corpus-v4.1 REF_LINE_HEIGHT pin; ≈18px
 * in the Round-4 default-serif era). Our renderer wraps a childless component's text in a
 * PlaceholderContent <span> whose default `padding: 4px` makes the same bar
 * ≈10px TALLER. With flush bars that height error was mostly hidden; once the
 * UA margins are restored (index.html `wpt-composed-mode` margin:revert), the
 * per-bar height error COMPOUNDS down a 10-bar test and drifts every bar off
 * its ref position — sinking SSIM. So in composed mode ONLY we drop the
 * placeholder padding to 0 (PlaceholderContent below), rendering text as tight
 * as the ref's <p>. Read-once module constant like WPT_MODE / FORCE_STATE; the
 * per-component `?wpt=1`-only path and the 327-pair baseline never set this
 * param, so their placeholder padding (and captures) stay byte-identical.
 */
const WPT_COMPOSED_MODE: boolean = (() => {
  if (typeof window === 'undefined') return false;
  return new URLSearchParams(window.location.search).get('wptComposed') === '1';
})();

/**
 * `?forceState=<state>` — the forced interaction state for this capture
 * run (spec 06 §6; docs/DYNAMIC_CAPTURE.md §1). CaptureGallery stamps the
 * verification marker (`data-force-state`) on every canvas; the CORE is
 * where the state is actually applied (options.forceState → every
 * rendered element gets the `force-<state>` class, which twins the real
 * pseudo-class on the SAME RuleBuilder rule — so a forced run resolves
 * byte-identically to real input). Same read-once module-constant
 * pattern as WPT_MODE (URL params can't change mid-capture; keeps the
 * hot render loop allocation-free). Values outside the runtime-v1 set
 * fall back to null (base-state render) — capture-screenshots.mjs's env
 * validation is the loud gate.
 */
const FORCE_STATE = (() => {
  if (typeof window === 'undefined') return null;                    // SSR — never forced
  const raw = new URLSearchParams(window.location.search).get('forceState');
  return raw && isRuntimeV1Condition(raw) ? raw : null;              // validated or dropped
})();

/**
 * Deterministic placeholder for `meta.sourceTag: 'img'` components
 * (issue #36 web slice — supplied to the core via resolveImageSource;
 * the wave-9 IR-gap rationale lives with the hook below). An inline SVG
 * data-URI so the capture needs no network fetch and the bytes can
 * never vary between runs:
 *   - 100×100 intrinsic size → defined natural size + 1:1 natural aspect
 *     ratio for object-fit / aspect-ratio-transfer behavior;
 *   - mid-gray field (#808080) + darker centered disc (#4a4a4a) → visible
 *     interior structure, so cover/contain/fill scaling and object-
 *     position offsets produce visibly different pixels under SSIM.
 * URL-encoded per RFC 2397 (only `#` needs escaping in this payload).
 */
const PLACEHOLDER_IMG_SRC =
  "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='100' height='100'%3E" +
  "%3Crect width='100' height='100' fill='%23808080'/%3E" +
  "%3Ccircle cx='50' cy='50' r='30' fill='%234a4a4a'/%3E%3C/svg%3E";

// ── wave-36 lane M1: the REAL replaced-element source ────────────────────────
//
// The placeholder above exists because "nothing on today's wire carries the
// image SOURCE". As of wave-36 something does: the extractor forwards the
// corpus-relative path of `<img src>` / `<embed src>` / `<object data>` /
// `<video poster>` as `meta.attrs.src` (tools/titan/extract-fixture.mjs,
// REPLACED_SRC_TAGS), and vite.config.ts serves the corpus under the prefix
// below. So a WPT capture can finally scale the image the test is about
// instead of a grey disc.
//
// WHY THAT WAS THE WHOLE BUG. object-fit decides HOW replaced content is
// scaled into its box; with fixed 100×100 placeholder bytes the box, the
// keyword and the position were all correct and the pixels were still wrong
// — measured on the wave-35 web map as 154 of 196 scored css-images object-*
// cells, with `object-fit: none` captures at 10.3% ink against a 1.9% ref
// (the 100×100 placeholder floods a 48×32 box the real 16×8 image barely
// dots) and every `<embed>`/`<object>`/`<video>` capture at 0.9% against
// 10.1% (those tags are outside TAG_ALLOWLIST and painted nothing at all).
//
// The placeholder STAYS as the fallback: the legacy 327-pair fixtures carry
// no `meta.attrs.src`, so their captures are byte-identical, and a wire whose
// src the extractor declined to deliver still gets stable bytes rather than a
// broken-image glyph.
const WPT_IMAGE_ROUTE = '/wpt-image/';

/**
 * The corpus path a component's replaced content should paint, as a URL —
 * or undefined when the wire carries none.
 *
 * `data:` and absolute URLs pass through verbatim (self-contained already —
 * the extractor forwards an authored data: URI unchanged); everything else is
 * a corpus-relative path and gets the route prefix. Each segment is
 * percent-encoded because the corpus has spaces and parentheses in some
 * support paths — the route decodes with decodeURIComponent, so the two
 * halves must agree on the escaping. `/` separators are preserved (encoding
 * them would defeat the route's own path resolution).
 */
function wptImageSrc(component: { meta?: { attrs?: Record<string, unknown> | null } | null }): string | undefined {
  const raw = component.meta?.attrs?.src;
  if (typeof raw !== 'string' || raw.length === 0) return undefined;
  if (/^(?:data:|https?:|\/\/)/i.test(raw)) return raw;
  return WPT_IMAGE_ROUTE + raw.split('/').map(encodeURIComponent).join('/');
}

/**
 * sourceTags whose content the wire delivers as an image and that the
 * harness therefore paints through a real `<img>` element.
 *
 * HARNESS DIVERGENCE, and a deliberate one. `embed` and `object` are on the
 * PRODUCTION renderer's DENYLISTED_TAGS (runtimes/web/src/renderer/
 * TagMapping.ts — "external-document embedding"), so the package will never
 * emit them and neither will this harness: rendering a real `<embed>` would
 * hand third-party corpus content a nested browsing context inside the
 * capture page. What Chromium paints for `<embed src="x.png">` / `<object
 * data="x.png">` is an image document, and `<video poster>` paints the poster
 * frame — in all three cases an `<img>` with the same source and the same
 * object-fit/object-position produces the same pixels, verified against the
 * browser-ref for this family. So the harness maps them to `<img>`: same
 * paint, none of the embedding surface.
 *
 * GATED on the wire actually carrying a source. Without one there is nothing
 * an <img> could paint that a <div> doesn't, and the demotion to <div> stays
 * byte-identical for every fixture that predates this lane.
 */
const REPLACED_IMG_SOURCE_TAGS: ReadonlySet<string> = new Set(['embed', 'object', 'video']);

interface ComponentRendererProps {
  /** Composed node: the flat-wire component + its slot-composed children. */
  node: ComposedNode;
  depth?: number;
}

/**
 * wave-26 lane WWS — the extractor's inter-sibling whitespace marker.
 *
 * Byte-parallel with `WS_AFTER_ROLE` in tools/titan/extract-fixture.mjs:
 * the extractor stamps `_role: 'ws-after'` on the EARLIER of two adjacent
 * siblings when SOURCE whitespace separated them, and the converter
 * forwards `_role` verbatim as IR v2 `meta.role` (the schema's documented
 * open-ended marker channel). Reading it here is what keeps the separator
 * below honest: the harness never invents a space, it only replays one the
 * source really had. Absent marker = flush source = flush DOM.
 */
const WS_AFTER_ROLE = 'ws-after';

/**
 * Did the source separate THIS sibling from the next one with whitespace?
 * The single read point for the marker — the extractor's banner promises
 * that promoting `_role: 'ws-after'` to a first-class `meta.wsAfter`
 * boolean (when a wire lane does the converter + schema hop) is a one-line
 * change on each side, and this function is the web side of that line.
 */
function isWsAfterMarked(node: ComposedNode): boolean {
  return node.component.meta?.role === WS_AFTER_ROLE;
}

/**
 * wave-26 lane WWS — sourceTags whose UA default display is inline-level,
 * consulted ONLY when the component declares no `display` of its own.
 * Byte-parallel with the extractor's INLINE_LEVEL_TAGS set. A component
 * with neither a declared display nor an inline-default tag is treated as
 * block-level, which is the conservative answer: whitespace between block
 * boxes renders nothing anyway (CSS 2.1 §9.2.2.1), so withholding the
 * separator there costs no pixels.
 */
const INLINE_LEVEL_SOURCE_TAGS: ReadonlySet<string> = new Set([
  'span', 'a', 'b', 'i', 'em', 'strong', 'code', 'small', 'sub', 'sup',
  'u', 's', 'q', 'abbr', 'cite', 'time', 'label', 'mark', 'bdi', 'bdo',
  'samp', 'kbd', 'var', 'img', 'input', 'select', 'button', 'textarea',
  'output', 'meter', 'progress', 'ruby', 'rt', 'rb',
]);

/**
 * wave-26 lane WWS — container `white-space` values under which a source
 * whitespace run does NOT collapse to a single space advance, so the
 * separator must decline. `pre` / `pre-wrap` / `break-spaces` preserve
 * spaces AND segment breaks (the extractor's WHITESPACE_PRESERVING set);
 * `pre-line` collapses spaces but preserves the segment break — in every
 * one of the four the ref paints a LINE BREAK where this hook would have
 * painted a space, which is a different wrong answer, not a fix.
 */
const WHITESPACE_PRESERVING_CONTAINERS: ReadonlySet<string> = new Set([
  'pre', 'pre-wrap', 'pre-line', 'break-spaces',
]);

/**
 * The component's DECLARED `display` keyword in CSS spelling
 * ('inline-block', 'flex', 'inline flow-root', …), or null when the wire
 * declares none. Mirrors detectDisplayType's tolerant data reading — the
 * engine emits `Display` either as a bare keyword string or as a
 * `{keyword}`/`{type}` object — but returns the RAW keyword instead of
 * collapsing it into the coarse DisplayType bucket, because the separator
 * predicate has to distinguish `inline-grid` (inline-level) from `grid`
 * (block-level) and detectDisplayType maps both to 'grid'.
 */
function declaredDisplayKeyword(properties: IRProperty[]): string | null {
  for (const prop of properties) {
    if (prop.type !== 'Display') continue;
    const keyword = typeof prop.data === 'string'
      ? prop.data
      : (prop.data as Record<string, unknown>)?.keyword ?? (prop.data as Record<string, unknown>)?.type;
    // IR keywords arrive SHOUTY_SNAKE from the Kotlin converter and
    // lowercase-hyphenated from hand-authored fixtures — normalise both.
    if (typeof keyword === 'string') return keyword.toLowerCase().replace(/_/g, '-');
  }
  return null;
}

/**
 * wave-26 lane WWS — is this composed sibling an INLINE-LEVEL box, i.e.
 * one that occupies horizontal space on a line where a collapsed space
 * advance is visible?
 *
 * Declared display wins outright (css-display-3 §2): anything spelled
 * `inline*` — inline, inline-block, inline-flex, inline-grid, inline-table,
 * the two-value `inline flow-root` — is inline-level; every other declared
 * value (block, flex, grid, table, list-item, contents, none) is not.
 * With no declared display the UA default decides, via the tag set above.
 */
function isInlineLevelSibling(node: ComposedNode): boolean {
  const declared = declaredDisplayKeyword(node.component.properties);
  if (declared !== null) return declared.startsWith('inline');
  const tag = node.component.meta?.sourceTag?.toLowerCase();
  return !!tag && INLINE_LEVEL_SOURCE_TAGS.has(tag);
}

/**
 * Detect the display/layout type from properties.
 */
type DisplayType = 'block' | 'flex-row' | 'flex-column' | 'grid' | 'inline' | 'none';

function detectDisplayType(properties: IRProperty[]): DisplayType {
  let displayType: DisplayType = 'block';
  let flexDirection = 'row';

  for (const prop of properties) {
    if (prop.type === 'Display') {
      const keyword = typeof prop.data === 'string'
        ? prop.data
        : (prop.data as Record<string, unknown>)?.keyword || (prop.data as Record<string, unknown>)?.type;

      if (typeof keyword === 'string') {
        switch (keyword.toLowerCase().replace(/_/g, '-')) {
          case 'flex':
          case 'inline-flex':
            displayType = 'flex-row';
            break;
          case 'grid':
            displayType = 'grid';
            break;
          case 'inline':
          case 'inline-block':
            displayType = 'inline';
            break;
          case 'none':
            displayType = 'none';
            break;
        }
      }
    }

    if (prop.type === 'FlexDirection') {
      const direction = typeof prop.data === 'string'
        ? prop.data
        : (prop.data as Record<string, unknown>)?.keyword;

      if (typeof direction === 'string' && direction.toLowerCase().includes('column')) {
        flexDirection = 'column';
      }
    }
  }

  // Update flex direction
  if (displayType === 'flex-row' && flexDirection === 'column') {
    displayType = 'flex-column';
  }

  return displayType;
}

/**
 * Sizing-property IR types whose presence in a selector/media bucket
 * means the bucket intends to own box geometry (the spec 06 dynamic-
 * sizing carve-out below).
 */
const SIZING_TYPES = ['Width', 'Height', 'MinWidth', 'MaxWidth', 'MinHeight', 'MaxHeight',
  'InlineSize', 'BlockSize', 'MinInlineSize', 'MaxInlineSize', 'MinBlockSize', 'MaxBlockSize'];

/**
 * The harness sizing calibration — the decorateStyles hook. Byte-for-byte
 * the containerStyles computation the pre-#41 renderer inlined; the
 * RendererParity suite pins the resulting HTML against the pre-refactor
 * golden. See the numbered divergence ledger in the file header for WHY
 * none of this belongs in the package default.
 */
function calibrateStyles(styles: CSSStyles, ctx: RenderContext): CSSStyles {
  const { component, hasChildren } = ctx;

  // Aspect-ratio fit-content suppression — see
  // tools/titan/investigations/swarm-001/css-sizing__block-aspect-ratio-032.json
  //
  // CSS aspect-ratio (css-sizing-4 §6.2) only transfers a size from the
  // constrained axis to the unconstrained axis when the cross axis is left
  // `auto`. Our default `width: 'fit-content'` initialiser (added to make
  // empty placeholder boxes hug their content the way SwiftUI/Compose do)
  // makes the inline size content-driven, which the spec treats as a
  // definite specification — so the height-to-width transfer never fires.
  //
  // Detection rule per the swarm-001 fixProposal: if the IR carries an
  // `aspect-ratio` AND exactly one of {width-axis, height-axis} is
  // unconstrained (no explicit Width/MinWidth/MaxWidth/InlineSize/
  // MinInlineSize/MaxInlineSize for the inline axis, or none of the
  // analogous block-axis properties), skip the `fit-content` initialiser
  // and the matching `min*` floor for the unconstrained axis. The browser
  // then performs the cross-axis transfer naturally.
  //
  // We read from the resolved `styles` object (post buildStyles spread)
  // because the SizeApplier converts every IR variant — physical, logical,
  // raw lengths, keywords — into the same CSS property names. That lets
  // us stay agnostic to which property type the parser produced.
  const hasAspectRatio = styles.aspectRatio !== undefined && styles.aspectRatio !== 'auto';
  const inlineAxisConstrained =
    styles.width !== undefined ||
    styles.minWidth !== undefined ||
    styles.maxWidth !== undefined ||
    styles.inlineSize !== undefined ||
    styles.minInlineSize !== undefined ||
    styles.maxInlineSize !== undefined;
  const blockAxisConstrained =
    styles.height !== undefined ||
    styles.minHeight !== undefined ||
    styles.maxHeight !== undefined ||
    styles.blockSize !== undefined ||
    styles.minBlockSize !== undefined ||
    styles.maxBlockSize !== undefined;
  // Only intervene when aspect-ratio + exactly-one-axis-constrained — the
  // case the spec actually covers. If both axes are constrained or neither
  // is, the browser doesn't transfer anyway and the existing fit-content
  // default is harmless.
  const aspectRatioInlineUnconstrained = hasAspectRatio && blockAxisConstrained && !inlineAxisConstrained;
  const aspectRatioBlockUnconstrained = hasAspectRatio && inlineAxisConstrained && !blockAxisConstrained;

  // Dynamic-sizing carve-out (spec 06) — do the component's selector/media
  // buckets redeclare box geometry? The synthetic min-width/min-height
  // floors below default to the BASE width/height, which is a no-op for
  // static components (floor == declared size) but actively fights a
  // bucket rule: `@media (max-width: 300px) { width: 120px !important }`
  // beats the inline `width: 200px` in the cascade, yet the stale inline
  // `min-width: 200px` floor re-clamps the used value back to 200 (CSS
  // sizing: used width = max(min-width, width)). Caught by MW_LayoutFlip
  // at the 250 px capture width. When ANY bucket declares a sizing
  // property we drop the size-derived floor to '0' — pixel-identical at
  // base state (the inline width/height still applies) but overridable by
  // the stylesheet path. Bucket-free components (the 327-pair baseline)
  // never enter this branch.
  const bucketsDeclareSizing =
    (component.selectors ?? []).some((s) => s.properties.some((p) => SIZING_TYPES.includes(p.type))) ||
    (component.media ?? []).some((m) => m.properties.some((p) => SIZING_TYPES.includes(p.type)));

  // Bug 1 — WPT block-flow widen carve-out — see
  // tools/titan/investigations/swarm-003/css-ui__negative-outline-offset.json
  //
  // The unconditional `width: 'fit-content' + minWidth:'50px' + minHeight:'30px'`
  // defaults are essential for the legacy 327-pair visual-test fixtures
  // (iOS/Android use intrinsic SwiftUI/Compose sizing — boxes hug their
  // content), but they actively break WPT tests whose pass criterion
  // depends on the styled element having its native block-flow body-width
  // layout (outline-offset, percentage backgrounds, calc()-extent widths,
  // any test where the box stretches to its containing block by default).
  //
  // Under WPT_MODE we skip the fit-content + minWidth/minHeight defaults
  // entirely so block-level elements get the browser's normal
  // width:auto / height:auto behaviour. The aspect-ratio carve-out is
  // a no-op in this branch since it was solving the same class of
  // problem (a different way) — WPT mode dominates. Legacy flow
  // (no `?wpt=1`) is unchanged.
  return WPT_MODE ? {
    // In WPT mode we want browser-default block-flow: width:auto
    // (stretches to containing block), height:auto (hugs content),
    // no synthetic minimum floor. The IR's own `width` / `min-*` /
    // `max-*` come in via the spread below and take precedence.
    //
    // COMPOSED mode must NOT clamp maxWidth: real WPT pages let wide
    // geometry overflow the viewport and the browser-ref crops at the
    // canvas edge (the composed canvas already has width:390px +
    // overflow:hidden, so cropping matches the ref by construction).
    // The clamp was squeezing multicol tests' wide column rows into the
    // canvas width, diverging from the ref's crop — the wave-10 frag-bg
    // diagnosis measured this as the PRIMARY web css-break mechanism
    // (background-image-000/002 web 0.33/0.53 with assets delivered).
    // Per-element WPT mode (non-composed) keeps the clamp: its capture
    // crops per component, where 100% still mirrors the ref viewport.
    ...(WPT_COMPOSED_MODE ? {} : { maxWidth: '100%' }),
    ...styles,
  } : {
    // Skip the `fit-content` initialiser when aspect-ratio needs to drive
    // the inline axis — see the block above. For every other component
    // (the 327-pair baseline) the existing default applies unchanged.
    ...(aspectRatioInlineUnconstrained ? {} : { width: 'fit-content' }),
    maxWidth: '100%',
    ...styles,
    // Ensure minimum dimensions for visibility, but ONLY when the IR
    // didn't declare an explicit max-width/max-height. Previously this
    // line set `minWidth: styles.width` which made `width: 300; max-width: 50`
    // resolve to 300 instead of 50 (CSS clamp: max(min, min(width, max)) → max(300,50)=300).
    // When max-* is declared we trust the IR fully and only fall back to a
    // hard 50/30 px floor if BOTH width and max-width are absent.
    // Respect IR-declared min/max first; only fall back to a height/
    // width-derived floor when the IR set NEITHER min nor max for that
    // axis. This keeps `width: 300; max-width: 50` resolving to 50 (was
    // forcing minWidth=300 → CSS clamp returned 300) AND
    // `height: 20; min-height: 120` resolving to 120 (was forcing
    // minHeight=20 → web ignored the larger min). Logical-axis variants
    // (min-/max-inline-size, min-/max-block-size) participate in the
    // same constraint resolution; in our LTR horizontal-tb canvas they
    // fold to width/height respectively, so we treat them as additional
    // sentinels that suppress the fallback.
    //
    // Aspect-ratio carve-out (swarm-001 css-sizing__block-aspect-ratio-032):
    // when we've intentionally left an axis unconstrained so the browser
    // can do aspect-ratio size transfer, we must NOT slap a min-* floor on
    // that axis either — a `min-width: 50px` would re-establish a
    // non-auto inline size and defeat the transfer the same way
    // `width: fit-content` did.
    //
    // Bug 3 — aspect-ratio + child intrinsic-size lift — see
    // tools/titan/investigations/swarm-003/css-sizing__block-aspect-ratio-015.json
    //
    // CSS Sizing 4 §6.2.2 says `min-width: auto` on an aspect-ratio box
    // resolves to the `min-content` of its contents. When the parent has
    // an aspect-ratio + a sized child, the spec wants the parent to grow
    // to fit the child's intrinsic width (which then makes both axes
    // definite and overrides the ratio transfer). Chromium's resolver
    // can lose that signal when the inner child is wrapped in our
    // placeholder shell, so make the rule explicit by injecting
    // `min-width: min-content` (resp. `min-height: min-content`) on the
    // aspect-ratio axis we left unconstrained, when there's a child to
    // lift from. `min-content` is harmless on the empty-box case (the
    // 032 fixture) — no children means min-content resolves to 0, so
    // the aspect-ratio transfer still fires as before.
    // Dynamic-sizing carve-out (see bucketsDeclareSizing above): when a
    // bucket redeclares geometry, the size-derived floor collapses to '0'
    // so the !important bucket rule owns the used value at every width.
    //
    // wave-35 lane B9 follow-up — the max-* suppression is SIZE-DERIVED
    // ONLY. The paragraph above conflates two different floors that this
    // one ternary produced:
    //
    //   (a) the SIZE-DERIVED floor `min-width: <the declared width>`,
    //       which is what actually broke `width: 300; max-width: 50`
    //       (used width = max(min, min(width, max)) = max(300, 50) = 300);
    //   (b) the hard 50/30 px PLACEHOLDER floor, which exists because a
    //       childless capture box has NO intrinsic inline contribution at
    //       all — PlaceholderContent's block-label span is `height: 0`
    //       with an `position: absolute` svg (see the ZERO LAYOUT
    //       FOOTPRINT comment there), so `width: fit-content` resolves to
    //       exactly 0px and the box disappears.
    //
    // Zeroing (a) under a declared max-* is correct and stays. Zeroing (b)
    // was collateral: with no declared width there is no size-derived
    // floor for the max-* to fight, so dropping the placeholder floor just
    // hands the box a 0px used width.
    //
    // MEASURED (this is the wave-35 regression this branch repairs):
    // fixtures/visual-test.json `Sizing_MaxWidthPercent`
    // (`max-width: 80%; height: 50px; background: #8e44ad`, no width).
    // Lane B9 widened the engine's extractLength to read the
    // `{type:'percentage', percentage:N}` wire for Max*/Min*, so
    // `styles.maxWidth` became '80%' where it had previously been dropped
    // — the box went from `min-width: 50px` (a 50x50 purple square, the
    // committed baseline on all three platforms) to `min-width: 0` →
    // `width: fit-content` → 0px → NO BOX AT ALL (2,500 px of diff, the
    // one regression in the 327-capture net). The percentage itself is
    // fine: the capture canvas is a definite 390px border-box with 16px
    // padding, so 80% resolves against a 358px containing block to
    // 286.4px and never binds. Nothing here is cyclic.
    //
    // This also restores PARITY with the natives, which never had the
    // max-* suppression at all: Compose gates the identical floor on
    // `hasExplicitWidth = any { type in [Width, MinWidth, InlineSize,
    // MinInlineSize] }` (runtimes/compose/.../core/renderer/
    // ComponentRenderer.kt) — MaxWidth is deliberately NOT in that list —
    // and SwiftUI's MinBoxFloor mirrors it. That is exactly why both
    // natives still render the 50x50 square byte-identically to baseline.
    //
    // Blast radius, MEASURED over fixtures/visual-test.json: only two of
    // the 109 components declare any max-* at all. `Sizing_MinMax`
    // declares `min-width: 100px` explicitly, so it bottoms out in the
    // FIRST disjunct and never reaches this branch; `Sizing_MaxWidthPercent`
    // is the regression itself. Every other capture is untouched by
    // construction, and the four other reachable states are unchanged:
    // buckets → '0' (either capping state), capped + declared width → '0',
    // uncapped → the pre-existing `width || 50px`.
    minWidth: aspectRatioInlineUnconstrained
      ? (hasChildren ? 'min-content' : undefined)
      : (styles.minWidth || styles.minInlineSize ||
        (bucketsDeclareSizing ? '0'
          : (styles.maxWidth || styles.maxInlineSize)
            // Capped axis: suppress only the size-derived floor (a). With
            // no declared width there is none, so the placeholder floor
            // (b) survives — a max-* cap is a ceiling, not a reason to
            // let the box collapse below the shared 50px minimum.
            ? ((styles.width || styles.inlineSize) ? '0' : '50px')
            // Uncapped axis: the historical floor, byte-for-byte.
            : (styles.width || styles.inlineSize || '50px'))),
    // Block-axis twin of the same rule. `max-height` with no declared
    // height collapses a childless box to 0px for exactly the same reason
    // (the block-label span contributes no height either), and Compose's
    // `hasExplicitHeight` gate likewise omits MaxHeight. No component in
    // fixtures/visual-test.json declares a max-height, so this half moves
    // no committed capture — it is kept in lock-step with the inline axis
    // so the two can't drift into different answers for the same question.
    minHeight: aspectRatioBlockUnconstrained
      ? (hasChildren ? 'min-content' : undefined)
      : (styles.minHeight || styles.minBlockSize ||
        (bucketsDeclareSizing ? '0'
          : (styles.maxHeight || styles.maxBlockSize)
            ? ((styles.height || styles.blockSize) ? '0' : '30px')
            : (styles.height || styles.blockSize || '30px'))),
    // Empty grid/flex → behave like a block so the placeholder doesn't get
    // inflated by track/flex sizing. Applied AFTER the spread so it always
    // wins for empty containers; explicit `display` from styles is dropped
    // here because grid/flex layout makes no visual sense with zero items.
    ...(hasChildren ? {} : (
      styles.display === 'grid' || styles.display === 'flex' ||
      styles.display === 'inline-grid' || styles.display === 'inline-flex'
        ? { display: 'block' }
        : {}
    )),
  };
}

/**
 * The harness sourceTag allowlist — divergence #3. We only switch to
 * tags whose browser-default semantics we positively want (lists,
 * headings, paragraphs, tables, details, inline companions). Anything
 * else falls back to <div> so captures don't acquire form-control
 * behaviour (<input>, <button>), embed handling (<iframe>, <object>),
 * or script-context tags (<script>, <style>). The PACKAGE default
 * (TagMapping.defaultMapTag) trusts the wire instead — production SDUI
 * wants native button/link/input semantics; captures must not.
 * Lowercasing happens in the core before the hook runs.
 */
const TAG_ALLOWLIST = new Set([
  'ol', 'ul', 'li',
  'p',
  'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
  'table', 'thead', 'tbody', 'tfoot', 'tr', 'td', 'th',
  'details', 'summary',
  // wave-35 lane B9 — the FIELDSET/LEGEND pair. These are form-ASSOCIATED
  // but not form CONTROLS: neither takes input, neither acquires a focus
  // ring, and neither is in WIDGET_TAGS — so the "captures must not acquire
  // form-control behaviour" reason that keeps <input>/<button> out does not
  // reach them. What they DO carry is a UA box model no other element has
  // and no <div> can imitate: html.css gives <fieldset> a 2px groove border,
  // asymmetric block padding (0.35em top / 0.625em bottom), 0.75em inline
  // padding, `min-inline-size: min-content` and a 2px inline margin, and it
  // NOTCHES that border around the rendered <legend>, which is laid out
  // inside the border-box instead of the content flow (HTML §15.3.9 "the
  // fieldset and legend elements"). Demoting both to <div> erased all of it.
  //
  // MEASURED on the frozen wave34-depth css-display slice (all three are
  // composed-vs-ref fails there): display-contents-fieldset-nested-legend
  // painted P / legend / ASS as three bare block lines with no box at all —
  // 0.272% ink coverage against the ref's 0.906%, a 3.3× deficit, at SSIM
  // 0.9449. The other two (display-contents-fieldset-002 0.6987,
  // display-contents-dynamic-fieldset-legend-001 0.7181) are the same
  // missing chrome multiplied over 17 and 16 fieldsets.
  //
  // The notch is the reason this has to be TAG mapping and not CSS: it is
  // not expressible as a border declaration at all, so the ONLY way a
  // capture can show it is to hand the browser a real <fieldset>/<legend>
  // and let the same UA stylesheet that drew the ref draw ours.
  //
  // Blast radius, MEASURED over the corpus: 53 of the 10681 bucket-A tests
  // contain `<fieldset` and 31 contain `<legend` (0.5%), and NONE of them
  // appear in any of the 29 frozen wave34-final gate slices — so this
  // mapping cannot move a single gate number by construction.
  'fieldset', 'legend',
  'blockquote', 'q',
  'dl', 'dt', 'dd',
  'figure', 'figcaption',
  'section', 'article', 'nav', 'header', 'footer', 'main', 'aside',
  // Bug 2 — emit <span> when F-G-EXTRACTOR forwards _tag:'span'. See
  // tools/titan/investigations/swarm-003/css-contain__content-visibility-hidden-and-innertext.json
  //
  // The CSS-Containment-2 spec says content-visibility:hidden does NOT
  // apply to non-atomic inline boxes (<span> with default display:inline
  // is the canonical non-atomic inline). Previously we always rendered
  // as <div>, which IS a block container, so the browser would
  // (correctly per its rule) hide the contents — but that's exactly
  // the opposite of what the test asserts. Emitting <span> lets the
  // browser observe the inline/block distinction the spec hinges on.
  'span',
  // Inline-level structural tags follow the same pattern — they need
  // to remain inline for surrounding inline-flow / generated-content
  // / whitespace-collapse rules to behave correctly.
  'strong', 'em', 'b', 'i', 'u', 's', 'mark', 'small', 'sub', 'sup',
  'code', 'kbd', 'samp', 'var', 'cite', 'dfn', 'abbr', 'time',
]);

/**
 * wave-35 lane B4 — the members of TAG_ALLOWLIST whose UA default display
 * is INLINE, i.e. every tag the allowlist maps to a real non-atomic inline
 * box. This is exactly the set for which the placeholder path's
 * `display: block` content wrapper is a category error (see
 * renderEmptyContent below): the element is an inline box, so a block box
 * inside it splits the inline into anonymous block boxes (CSS 2.1
 * §9.2.1.1) and destroys the very line box the test measures.
 *
 * MUST stay a SUBSET of TAG_ALLOWLIST — a tag outside the allowlist is
 * mapped to <div> (a block container), where the placeholder wrapper is
 * correct and load-bearing. The `renders as its own inline element` pin in
 * ComponentRenderer.spanInline.test.tsx checks that subset relation end to
 * end (render → assert the emitted element name) so the two literals can
 * never silently drift apart.
 *
 * Deliberately EXCLUDED, each for a reason, not an oversight:
 *   - `label`, `bdi`, `bdo`, `rt`, `rb` — inline-level on the wire but NOT
 *     in TAG_ALLOWLIST, so they render as <div>; promoting them is a
 *     tag-MAPPING change, a different measurable.
 *   - `a`, `button`, `input`, `select`, `textarea`, `option`, `meter`,
 *     `progress` — WIDGET_TAGS, already served by the first branch of the
 *     same early return (wave-20 W1); `img` bypasses into the core's
 *     void-element path.
 *
 * Measured population this closes (deduped by section+component across the
 * archived bucket-A run IRs, childless components only, span excluded
 * because wave-31 already covered it): q 123 · em 4 · code 3 · sub 1 ·
 * sup 1 · b 1 = 133 leaves. `q` dominates and is almost entirely the
 * css-content `quotes-0NN` family, where the UA `content: open-quote` /
 * `close-quote` pseudos are inline and were being split off the text by
 * exactly this wrapper — which is why css-content is scored alongside the
 * four sections the lane brief named.
 */
const INLINE_ALLOWLISTED_TAGS: ReadonlySet<string> = new Set([
  'span', 'q',
  'strong', 'em', 'b', 'i', 'u', 's', 'mark', 'small', 'sub', 'sup',
  'code', 'kbd', 'samp', 'var', 'cite', 'dfn', 'abbr', 'time',
]);

/**
 * The complete harness calibration, handed to the shared core on every
 * render. Module-level constant: the hooks close over the read-once URL
 * modes (WPT_MODE / FORCE_STATE), and a stable identity keeps the core's
 * render path allocation-free.
 */
const HARNESS_OPTIONS: RendererOptions = {
  // Divergence #1: display:none renders nothing at all — a hidden
  // component must never contribute a capture canvas or bleed paint
  // into a neighbour's screenshot crop.
  shouldRender: ({ component }) => detectDisplayType(component.properties) !== 'none',
  // Divergence #2: the sizing calibration (see calibrateStyles).
  decorateStyles: calibrateStyles,
  // Divergence #3: allowlist mapping — except `img`, which bypasses the
  // allowlist into the core's void-element branch (issue #36 web slice:
  // replaced-element CSS needs a REAL <img> box even on captures), and —
  // wave-20 W1 (RC2) — except the form/widget tags in WPT capture mode:
  // the WPT browser-ref paints real Chromium widget chrome, so demoting
  // `<input type=checkbox checked>` to a <div> WAS the divergence (the
  // css-ui family captured bare text against native checkboxes). WPT-mode
  // widgets pass through and the core applies `meta.attrs`; focus-ring
  // risk is neutralised by the decorateProps inert hook below. The legacy
  // 327-pair flow (no `?wpt=1`) keeps the demotion byte-for-byte.
  // wave-36 lane M1 adds ONE branch: in WPT capture mode a replaced element
  // whose source the wire delivers (embed/object/video — see
  // REPLACED_IMG_SOURCE_TAGS) resolves to <img> so the browser applies the
  // component's object-fit/object-position to real content. Every other
  // input to this function is unchanged, and the branch cannot fire outside
  // `?wpt=1` or without a wire src, so the 327-pair DOM stays byte-identical.
  mapTag: (tag, ctx) =>
    tag === 'img'
      ? 'img'
      : (WPT_MODE && tag && REPLACED_IMG_SOURCE_TAGS.has(tag) && wptImageSrc(ctx.component) !== undefined)
        ? 'img'
        : (WPT_MODE && tag && WIDGET_TAGS.has(tag))
          ? tag
          : (tag && TAG_ALLOWLIST.has(tag) ? tag : 'div'),
  // wave-20 W1: the last word on element props — WPT-mode widgets get
  // `inert` (no hover/focus/interaction states, React 19 boolean) plus
  // `tabIndex: -1` (never sequentially focusable), so a passed-through
  // control can never acquire a focus ring or interaction chrome the
  // never-focused browser-ref page doesn't show. Identity for every
  // other element AND for the whole non-WPT flow (byte-stable DOM).
  decorateProps: (props, elementName) =>
    WPT_MODE && WIDGET_TAGS.has(elementName)
      ? { ...props, inert: true, tabIndex: -1 }
      : props,
  // Divergence #4: childless components render the placeholder label
  // instead of an empty element, so empty fixtures stay identifiable
  // against iOS/Android placeholders. The LABEL now draws as the shared
  // BLOCK FONT (pinned fill + geometry — see BlockFontLabel), so all
  // three platforms rasterize it byte-identically; the bg-luminance /
  // explicit-color machinery below still drives real `text` content,
  // which keeps rendering as genuine glyphs (WPT ref comparability).
  //
  // PlaceholderContent receives the parent's resolved background color so
  // TEXT colour can flip to dark-on-light or light-on-dark, matching
  // iOS's `PlaceholderLabel.resolvedColor` (luminance > 0.6 → dark text);
  // the raw `styles` in the context is the same pre-decoration engine
  // output the pre-#41 renderer read. The explicit `color` passthrough
  // mirrors iOS/Android's `textColor` parameter (see PlaceholderContent).
  renderEmptyContent: ({ component, styles }) => {
    const text = component.text;
    const hasText = typeof text === 'string' && text.length > 0;
    // wave-20 W1: WPT-mode widget passthrough renders REAL content — the
    // bare text node, exactly the source markup shape
    // (`<button>button</button>`, `<option>select</option>`). The
    // placeholder span's display:block + padding would perturb native
    // widget content layout (button centering, option rows) vs the
    // Chromium ref. The legacy flow never enters: widgets are demoted to
    // <div> there and keep the placeholder label byte-for-byte.
    // wave-27 lane CBAKE B-RC2 — `<li>` joins that early return. The
    // placeholder path wraps the item's text in a `display: block` span,
    // which starts a new block box AFTER the browser's `::marker` — so
    // under `list-style-position: inside` (the whole css-counter-styles
    // corpus) the marker landed on its own line and every item was twice
    // as tall as the reference. A bare text node keeps the marker and the
    // content in one line box, which is exactly the source markup shape.
    // wave-31 lane S — `<span>` joins the same early return, for the same
    // reason one level down. The extractor started forwarding `_tag: 'span'`
    // this wave (tools/titan/extract-fixture.mjs GENERIC_WRAPPER_TAGS
    // banner), so TAG_ALLOWLIST finally maps a surviving span to a real
    // <span> element — but the placeholder path then put a
    // `display: block` span INSIDE it, and a block box inside an inline box
    // splits the inline into anonymous block boxes (CSS 2.1 §9.2.1.1). The
    // element was inline; its CONTENT was not; the line box the test
    // measures never formed. A bare text node is the source markup shape —
    // `<span>X</span>` — so the span's text participates in the parent's
    // inline formatting context exactly like the browser-ref's.
    //
    // A textless span returns null (same as `li`), which leaves
    // `<span class="sc-…"></span>`: an EMPTY inline box, which is precisely
    // what the source has. The old placeholder emitted a 0-content BLOCK
    // there, which broke the line just as hard as a text one.
    //
    // wave-35 lane B4 — the rest of the inline family joins, which is the
    // follow-up wave-31 scoped OUT and named ("a SEPARATE measurable
    // change"). Taking it now, as its own measurable, because the argument
    // is tag-independent: `<em>`, `<code>`, `<q>`, `<sub>`… are every bit
    // as non-atomic-inline as `<span>`, so a `display: block` wrapper
    // splits their line box by the same CSS 2.1 §9.2.1.1 rule. The one
    // thing that was genuinely span-specific in wave-31 was the WIRE — the
    // extractor had just started emitting `_tag: 'span'` — and the other
    // inline tags have carried their tags since wave-1, so this half was
    // always the only half missing. The gate is INLINE_ALLOWLISTED_TAGS
    // (above): tags the allowlist maps to a real inline element, never a
    // <div>-demoted one, where the placeholder wrapper is still correct.
    //
    // Composed-WPT only (WPT_MODE): the 327-pair baseline never sets
    // `?wpt=1`, so every committed capture keeps the placeholder
    // byte-for-byte — same gate wave-27 and wave-31 shipped behind.
    const wTag = component.meta?.sourceTag?.toLowerCase();
    if (WPT_MODE && wTag && (WIDGET_TAGS.has(wTag) || wTag === 'li' || INLINE_ALLOWLISTED_TAGS.has(wTag))) {
      return hasText ? text : null;
    }
    // Forward the component's IR-resolved line-height (if any) so the
    // composed-mode line-height pin can DEFER to it — a test that declares
    // its own line-height keeps it; only bare text (no declaration) gets the
    // ref-matching default. `lineHeight` may be number or string in CSS-in-JS.
    const irLineHeight = styles.lineHeight !== undefined ? String(styles.lineHeight) : undefined;
    return (
      <PlaceholderContent
        name={component.name}
        text={hasText ? text : undefined}
        backgroundColor={typeof styles.backgroundColor === 'string' ? styles.backgroundColor : undefined}
        explicitColor={typeof styles.color === 'string' ? styles.color : undefined}
        irLineHeight={irLineHeight}
        // The USED px width approximation drives the shared block-label
        // truncation `8 + n*ADVANCE <= width - 8`: the smallest of the
        // declared width / inline-size and their max-* caps (CSS clamp:
        // max-width beats width — mobile truncates against its MEASURED
        // width, so web must honour the cap too or the label overflows
        // a `width:300; max-width:50` box mobile would truncate).
        // undefined = content-sized box (fit-content hugs the label, so
        // nothing can overflow and no truncation applies). min-* floors
        // only ever WIDEN the box — safe to ignore for truncation.
        componentWidth={usedPxWidth(styles)}
      />
    );
  },
  // Mixed-content text renders inside a plain inheriting <span> (the
  // swarm-002 Bug 1 shape) — the parent's color / text-decoration /
  // font-* reach the glyphs via inheritance, and tests/tooling can
  // target the run. The package default is a bare text node.
  renderText: (text) => <span>{text}</span>,
  // Divergence #5: the image source for <img>. The wire's own
  // `meta.attrs.src` wins when it has one (wave-36 lane M1 — a corpus path
  // routed through /wpt-image/, see wptImageSrc); otherwise the harness
  // substitutes the deterministic placeholder, which is what keeps a
  // source-less <img> at fixed bytes → fixed pixels → stable captures. The
  // hook is still the ONE place a src is chosen, so the core stays free of
  // harness routing knowledge.
  resolveImageSource: (ctx) => wptImageSrc(ctx.component) ?? PLACEHOLDER_IMG_SRC,
  // Forced-state capture hook (spec 06 §6) — validated URL param.
  forceState: FORCE_STATE,
  // Wave-19 lane FLOAT — CSS 2.1 §9.5 float-run grouping, WPT capture
  // ONLY (pin P8; the legacy 327-pair flow never sets `?wpt=1`, so its
  // DOM stays byte-identical). Each run of ≥2 consecutive left-floating
  // children gets a `display:flow-root; width:max-content` wrapper so
  // float rows break ONLY at `<br clear>` markers: the captured IR's
  // synthetic 100px root frames otherwise wrap justify-self-001's rows
  // at an artifact width (3/2/3/2/3/1) the browser-ref — laid against
  // the ≥360px body — never saw (ref rows 3/2/5/4). See
  // engine/layout/FloatRowPacking.ts (the natives' pure-packer twin).
  planChildRuns: (children, ctx) => {
    // P8 — WPT capture only; every other flow keeps the pure default.
    if (!WPT_MODE) return null;
    // Block flow only — flex/grid children are items, not floats
    // (css-flexbox-1 §3 / css-grid-1 §6: float has no effect on them).
    const d = ctx.styles.display;
    if (d === 'flex' || d === 'grid' || d === 'inline-flex' || d === 'inline-grid') return null;
    // Per-sibling facts through the engine's Float/Clear extractors.
    const facts = children.map((child) => {
      // CSS 2.1 §9.7: position:absolute/fixed forces float → none —
      // an out-of-flow child never joins (or breaks) a run.
      const outOfFlow = child.component.properties.some(
        (p) => p.type === 'Position' && (p.data === 'ABSOLUTE' || p.data === 'FIXED'),
      );
      if (outOfFlow) return { floatsLeft: false, clearBreaksLeft: false };
      // The clear-break marker must be childless (pin P2) — a clear on
      // a content box is real layout, out of the narrow contract.
      return floatChildFacts(child.component.properties, child.children.length > 0);
    });
    // Segment once (pins P1/P2); run-free containers keep the default.
    const segments = segmentFloatRuns(facts);
    if (!segments.some((s) => s.isRun)) return null;
    // Runs wrap (BFC + max-content + optional br strut); singles stay
    // direct siblings — sibling order is preserved by construction.
    return {
      segments: segments.map((s) =>
        s.isRun
          ? { indices: s.indices, wrapperStyle: floatRunWrapperStyle(s.strutted) }
          : { indices: s.indices }),
    };
  },
  // Wave-20 W2 follow-up — inter-widget whitespace, WPT capture ONLY.
  // The source markup separates every form control by collapsed
  // whitespace (`<input …> <input …>`), which the browser-ref lays out
  // as one 16px-font space advance (~4.16px — the natives pin it as
  // UAWidgetIntrinsics.atomGapPx). The flat component wire carries no
  // inter-element text nodes, so the composed harness packed inline
  // widgets FLUSH and every wrap point shifted vs the ref (checked on
  // appearance-auto-001: ref row 2 = textarea 184 + input-button 74 +
  // input-submit 77 + input-reset 67 + 3 spaces ≈ 414 < 500 content px,
  // and the 129px range then WRAPS to row 3 — flush packing moved that
  // boundary). Re-inserting a REAL ' ' text node between consecutive
  // widget-tag siblings is the DOM-honest mirror: the browser collapses
  // and measures it natively, flex/grid parents ignore whitespace-only
  // nodes (css-flexbox-1 §4), and blockified widgets collapse it to
  // nothing (CSS 2.1 §9.2.2.1) — all self-correcting, no arithmetic
  // here. Legacy 327-pair flow (no `?wpt=1`) returns null → the DOM
  // stays byte-identical.
  //
  // Wave-26 lane WWS EXTENDS that rule past the widget families to ANY
  // adjacent inline-level pair, because the same dropped text nodes move
  // ordinary inline-block boxes too. MEASURED: filter-effects'
  // backdrop-filter-clip-rect-2 lays three 100px `display:inline-block`
  // boxes per row, one per source line; the ref collapses each newline+
  // indent to one space advance and puts them at x = 0 / 104.5 / 209
  // while our flush canvas put them at 0 / 100 / 200 — boxes 2 and 3 off
  // by 4.5 and 9 px, the whole 0.924 web gap, and on wider rows the same
  // displacement walks the wrap boundary.
  //
  // Both figures are MEASURED (puppeteer, ref page under the real
  // capture-browser-ref.mjs injection: Inter @16px, line-height 1.25);
  // with the separator in place the composed boxes land on 0 / 104.5 / 209
  // — an EXACT match, not an approach. The 4.16px in the wave-20 widget
  // rule's comment above is a different face's advance; do not unify them.
  // Nothing here does arithmetic on the number: the hook emits a real text
  // node and the browser measures the advance in the capture's own font.
  //
  // The extension is gated on the extractor's `ws-after` marker
  // (meta.role — see WS_AFTER_ROLE), so it can only ever REPLAY whitespace
  // the source really had; a flush-authored `<span>a</span><span>b</span>`
  // carries no marker and keeps its flush DOM. The wave-20 widget rule
  // above stays UNCONDITIONAL: it predates the marker, its captures are
  // baselined against it, and re-gating it would silently move css-ui.
  renderChildSeparator: (prev, next, ctx) => {
    // P8-style gate: capture calibration only ever fires under ?wpt=1.
    if (!WPT_MODE) return null;
    // Both neighbours must be widget-identity tags (the wire-contract
    // set — byte-parallel with the extractor's WIDGET_ATTR_TAGS and the
    // natives' P15 atom families).
    const prevTag = prev.component.meta?.sourceTag?.toLowerCase();
    const nextTag = next.component.meta?.sourceTag?.toLowerCase();
    if (prevTag && nextTag && WIDGET_TAGS.has(prevTag) && WIDGET_TAGS.has(nextTag)) return ' ';
    // ── wave-26 WWS: the inline-level extension ──────────────────────
    // COMPOSED capture only. The composed canvas is the one surface that
    // reproduces the ref PAGE's line boxes, so an inter-atom advance is
    // meaningful there; the per-component `?wpt=1` capture crops each
    // component on its own canvas with no page geometry to match, and
    // widening it would move captures no ref-page comparison reads.
    if (!WPT_COMPOSED_MODE) return null;
    // The source must actually have separated them (the whole honesty
    // gate — see WS_AFTER_ROLE). Marker sits on the EARLIER sibling.
    if (!isWsAfterMarked(prev)) return null;
    // Only an INLINE FORMATTING CONTEXT turns a collapsed space into a
    // visible advance. Flex/grid containers drop whitespace-only children
    // outright (css-flexbox-1 §4 / css-grid-1 §6), so a separator there is
    // dead DOM bytes — skip explicitly rather than lean on that.
    const d = ctx.styles.display;
    if (d === 'flex' || d === 'grid' || d === 'inline-flex' || d === 'inline-grid') return null;
    // A whitespace-PRESERVING container replays the run verbatim instead of
    // collapsing it (CSS Text §4.1.1): the ref shows the source's real
    // newline + indent there, and injecting one space would substitute a
    // different wrong answer for the current one. Out of contract — the
    // preserved-run case needs the ordered inline-run wire, not this hook.
    // Set mirrors the extractor's WHITESPACE_PRESERVING plus `pre-line`
    // (which collapses spaces but KEEPS the newline, so the ref breaks the
    // line where we would have put a space).
    //
    // KNOWN REACH LIMIT, measured not assumed: `ctx.styles` is the RAW
    // engine output for THIS container's own declared properties, so the
    // gate catches `white-space` declared ON the container and NOT a value
    // inherited from an ancestor — `<div style="white-space:pre"><div>
    // <span>a</span> <span>b</span></div></div>` still emits the space.
    // Resolving that needs the inherited cascade, which this hook has no
    // handle on (it sees prev/next/container, not the ancestor chain).
    // Bounded cost: in an inherited-pre context the ref paints a LINE BREAK
    // and both our variants keep one line, so the separator moves an
    // already-wrong layout by one space advance — it cannot turn a passing
    // comparison into a failing one. Promote to a resolved-style read when
    // the renderer grows an inheritance context.
    const ws = ctx.styles.whiteSpace;
    if (typeof ws === 'string' && WHITESPACE_PRESERVING_CONTAINERS.has(ws)) return null;
    // Both neighbours must be inline-level, or there is no line box for
    // the advance to land on (whitespace between blocks paints nothing —
    // CSS 2.1 §9.2.2.1 — so this is precision, not correctness).
    if (!isInlineLevelSibling(prev) || !isInlineLevelSibling(next)) return null;
    // A REAL collapsed-whitespace text node: the browser measures the
    // advance in the capture's own font, exactly as it does in the ref.
    return ' ';
  },
};

/**
 * Render a single composed IR node (one component + composed children)
 * with the harness calibration applied at every composition depth.
 */
export function ComponentRenderer({ node, depth = 0 }: ComponentRendererProps) {
  // Everything happens in the shared core; the skin only supplies hooks.
  return <NodeRenderer node={node} depth={depth} options={HARNESS_OPTIONS} />;
}

/**
 * Parse a CSS-in-JS length into an absolute px number, or undefined when
 * it isn't one. The engine emits absolute lengths as `'<n>px'` strings
 * (spec 02 — everything normalizes to px); bare numbers are tolerated for
 * robustness. Percentages / keywords / calc() return undefined — a
 * non-absolute width can't drive the shared block-label truncation, so
 * the label renders untruncated (the box is content- or context-sized).
 */
function parsePx(value: unknown): number | undefined {
  // Bare finite number → already px (React treats numbers as px too).
  if (typeof value === 'number' && Number.isFinite(value)) return value;
  // `'<n>px'` string → the engine's canonical absolute-length spelling.
  if (typeof value === 'string') {
    const m = value.match(/^(\d+(?:\.\d+)?)px$/);
    if (m) return parseFloat(m[1]);
  }
  // Anything else is not an absolute px width.
  return undefined;
}

/**
 * Approximate the component's USED inline width from raw engine styles,
 * for the shared block-label truncation. CSS resolves used width as
 * max(min-width, min(width, max-width)) — the min-* floor only ever
 * WIDENS the box (harmless for truncation), so the binding value is the
 * smallest of the declared size and its max-* cap, across both the
 * physical and logical spellings (inline-size folds to width in our LTR
 * horizontal-tb canvas). No absolute candidate → undefined (content-
 * sized box: fit-content hugs the label, nothing can overflow).
 */
function usedPxWidth(styles: CSSStyles): number | undefined {
  // Collect every absolute-px inline-axis constraint the engine emitted.
  const candidates = [styles.width, styles.inlineSize, styles.maxWidth, styles.maxInlineSize]
    // Non-absolute values (percentages, keywords, calc()) drop out here.
    .map(parsePx)
    // Keep only real numbers — Math.min over the survivors is the cap.
    .filter((v): v is number => v !== undefined);
  // No constraint at all → content-sized (caller treats as Infinity).
  return candidates.length > 0 ? Math.min(...candidates) : undefined;
}

/**
 * Placeholder content for components without children.
 */
interface PlaceholderContentProps {
  name: string;
  /**
   * Inner element text content from the IR (`IRComponent._text`). When
   * provided, takes precedence over the placeholder name AND over the
   * WPT_MODE empty-string suppression — i.e. it's the actual visible
   * content of the component, not a debug label. Set by the WPT extractor
   * for fixtures with styled <p>/<span>/<h*> elements; absent for the
   * legacy component-style fixtures where the harness still draws a
   * synthetic placeholder.
   *
   * See tools/titan/investigations/swarm-001/css-color__color-001.json.
   */
  text?: string;
  /** Resolved CSS color of the parent element (e.g. "rgb(255,255,255)"). */
  backgroundColor?: string;
  /**
   * Explicit text colour from CSS `color` on the parent element. When
   * provided, overrides the bg-luminance-derived contrast pick — the
   * placeholder renders in this exact colour. Mirrors the `textColor`
   * passthrough in iOS `PlaceholderLabel` and Android `PlaceholderContent`.
   */
  explicitColor?: string;
  /**
   * The component's IR-resolved `line-height` (stringified), when the IR
   * declared one. Used ONLY by the composed-mode line-height pin below: when
   * present, the pin defers to this value so a test's own line-height is
   * honoured; when absent, bare text gets the ref-matching default so our
   * text bar height matches the browser-ref's default-font line box.
   */
  irLineHeight?: string;
  /**
   * The component's declared absolute width in px (undefined when the
   * box is content-sized or the width isn't an absolute length). Feeds
   * the shared-spec block-label truncation `8 + n*ADVANCE <= width - 8`;
   * only the LABEL branch reads it — real `text` content never truncates.
   */
  componentWidth?: number;
}

/**
 * Parse a CSS color string into linear-ish [r,g,b] in 0-1, or null if we
 * can't. Only handles the forms our StyleEngine emits today: `rgb(...)`,
 * `rgba(...)`, `#RGB`, `#RRGGBB`. Anything else (gradients, var(),
 * named colors) returns null and the placeholder falls back to the
 * "dark bg" branch — same default as iOS / Android.
 */
function parseRgb(css: string | undefined): [number, number, number] | null {
  if (!css) return null;
  const s = css.trim();
  // #RRGGBB or #RGB
  const hex = s.match(/^#([0-9a-f]{3,8})$/i);
  if (hex) {
    const h = hex[1];
    if (h.length === 3 || h.length === 4) {
      // #RGB → expand each digit
      return [
        parseInt(h[0] + h[0], 16) / 255,
        parseInt(h[1] + h[1], 16) / 255,
        parseInt(h[2] + h[2], 16) / 255,
      ];
    }
    if (h.length === 6 || h.length === 8) {
      return [
        parseInt(h.slice(0, 2), 16) / 255,
        parseInt(h.slice(2, 4), 16) / 255,
        parseInt(h.slice(4, 6), 16) / 255,
      ];
    }
    return null;
  }
  // rgb()/rgba(): pull the first three numeric components, sRGB.
  const rgb = s.match(/rgba?\(\s*([0-9.]+)[,\s]+([0-9.]+)[,\s]+([0-9.]+)/i);
  if (rgb) {
    return [
      parseFloat(rgb[1]) / 255,
      parseFloat(rgb[2]) / 255,
      parseFloat(rgb[3]) / 255,
    ];
  }
  return null;
}

function PlaceholderContent({ name, text, backgroundColor, explicitColor, irLineHeight, componentWidth }: PlaceholderContentProps) {
  // Critical: inherit font properties from the parent so typography fixtures
  // render at their declared sizes/weights/etc. The previous implementation
  // hardcoded `fontSize: '11px'` here, which clobbered every Typography_*
  // component that had no children — the sidecar reporter caught it as
  // "ios=24 web=11" on Typography_Size and friends. Inheritance lets the
  // parent component's applied styles actually reach the rendered glyphs.
  //
  // Color resolution mirrors iOS `PlaceholderLabel.resolvedColor` byte-for-byte:
  //   • luminance = 0.299·R + 0.587·G + 0.114·B  (Rec.601 luma)
  //   • > 0.6 → dark text  Color(white: 0.2).opacity(0.7)  ≈ rgba(51,51,51,0.7)
  //   • else  → light text Color(white: 0.93).opacity(0.7) ≈ rgba(237,237,237,0.7)
  //   • no bg → light branch (matches iOS fallback when `bg.rgbComponents` nil)
  // This pulls Card_Complete / Input_Field / Outline_Solid / Button_Primary /
  // Tag_Chip etc. — every light-bg placeholder fixture — back into pixel
  // parity with iOS without touching the surrounding renderer.
  // Honour an explicit `color` from the IR before falling back to the
  // bg-luminance contrast pick. Without this, fixtures that set
  // `color: #e74c3c` (red) on a placeholder-only component get rendered
  // in dark grey because the auto-pick is keyed solely on background
  // brightness — masking real cross-platform divergences (iOS/Android
  // both honour explicit text colour, web didn't).
  const rgb = parseRgb(backgroundColor);
  const luminance = rgb ? 0.299 * rgb[0] + 0.587 * rgb[1] + 0.114 * rgb[2] : 0;
  // When the IR declares an explicit `color`, INHERIT it from the container
  // instead of re-stating the literal value on this span. The container's
  // inline style already carries that exact value, so base renders are
  // pixel-identical either way — but a dynamic-styling rule (spec 06: a
  // `:focus` selector bucket recoloring text, forced or real) overrides the
  // CONTAINER's color via `!important`, and a literal inline color here
  // would clobber the cascade and keep the glyphs at the stale base value
  // (caught by DS_StateStack forced-focus capture: text never flipped).
  // `inherit` lets the state-resolved color reach the visible text, which
  // is exactly what the native placeholders get from resolved styles.
  // corpus-v4.1 BLACK-ink sub-boundary (WPT mode only): a real WPT page
  // paints default prose in the UA `color: CanvasText` BLACK, and the
  // browser-ref now injects the same spec black
  // (capture-browser-ref.mjs `:where(body) { color:#000 }`). Through
  // corpus-v4.0 this span's no-bg fallback was the near-white
  // rgba(237,237,237,0.7) — invisible on the v4 white canvas exactly like
  // the ref's old white ink, so default-ink text tests passed VACUOUSLY
  // (neither side showed the text). Under WPT_MODE the colorless bottom-out
  // is now opaque #000, in lock-step with the native WPT-mode bottom-outs
  // (Compose WPT_DEFAULT_TEXT_INK, SwiftUI WPTCanvas.textInk). The
  // luminance contrast pick below is the 327-pair stage contract (mirrors
  // iOS PlaceholderLabel.resolvedColor) and stays byte-identical — WPT_MODE
  // is only ever true under `?wpt=1`, which no baseline capture sets.
  const color = explicitColor
    ? 'inherit'
    : WPT_MODE
      ? '#000000'                  // WPT default ink — spec CanvasText black
      : (luminance > 0.6
        ? 'rgba(51, 51, 51, 0.7)'    // dark on light
        : 'rgba(237, 237, 237, 0.7)'); // light on dark (and the no-bg fallback)
  // Visible-text resolution priority — see
  // tools/titan/investigations/swarm-001/css-color__color-001.json
  //
  //   1. `text` from IR (component._text) — the actual element text content
  //      preserved by the WPT extractor. Takes precedence over EVERYTHING
  //      including WPT_MODE: this IS the real content, not debug noise.
  //      Without this branch, css-color / css-text / css-fonts WPT tests
  //      render an empty card and sit permanently at structural-divergence.
  //
  //   2. WPT_MODE without `text` → empty string. Preserves the wrapping
  //      <span> (so an empty grid item still takes a track slot) but
  //      suppresses the pHash-poisoning `wpt__css-…__N` placeholder label
  //      that pilot-001 identified.
  //
  //   3. Legacy 327-pair flow (no `?wpt=1`, no `text`) → component name
  //      with underscores stripped. Unchanged from before.
  const visibleText = text !== undefined
    ? text
    : (WPT_MODE ? '' : name.replace(/_/g, ' '));
  // BLOCK-LABEL branch gate: only the synthetic debug LABEL (case 3 —
  // legacy flow, no IR text, not WPT-suppressed) renders as the shared
  // block font. Real `text` content (case 1) stays a genuine text node —
  // WPT captures compare against a Chromium browser-ref that renders real
  // fonts, so block-fonting actual content would wreck that comparison —
  // and the WPT_MODE suppression gate (case 2, empty string) is untouched.
  const isBlockLabel = text === undefined && !WPT_MODE;
  // Lay out the label once per render: uppercase + unknown→'-', truncated
  // to the declared component width (undefined → Infinity: a content-sized
  // box hugs the label, nothing can overflow). Geometry is pinned by the
  // shared spec so iOS/Android produce the same rects for the same string.
  const blockLayout = isBlockLabel ? layoutBlockLabel(visibleText, componentWidth ?? Infinity) : null;
  return (
    <span
      style={{
        display: 'block',
        // Composed WPT mode drops the placeholder padding to 0 so a text bar
        // is exactly one line-box tall — matching the browser-ref's native
        // <p> height (GAP 1 height half; see WPT_COMPOSED_MODE above). Every
        // other path (per-component `?wpt=1`, the 327-pair baseline) keeps the
        // 4px label breathing room, byte-for-byte unchanged.
        // BLOCK-LABEL branch: padding must be 0 — the shared-spec (8, 6)
        // origin is supplied by the svg's absolute offset below, and any
        // span padding would shift the rects off the cross-platform grid.
        padding: isBlockLabel ? 0 : (WPT_COMPOSED_MODE ? 0 : '4px'),
        // ZERO LAYOUT FOOTPRINT (block-label branch only): the label is
        // dev chrome, not content — it must not contribute flow height.
        // The first block-font cut let the svg occupy flow space, and the
        // three platforms' old text line boxes differed (~19px web vs
        // ~13px natives), so auto-height components rendered different
        // canvas heights (web 138 vs iOS 132 on the color fixture) and
        // every pixel below the label shifted — X-web pairs cratered while
        // iOS-Android agreed at 1.000. A 0-height positioned wrapper +
        // absolutely-placed svg pins the label at (8,6) with NO effect on
        // the component's own auto-height, identically on all platforms.
        ...(isBlockLabel ? { height: 0, position: 'relative' as const, overflow: 'visible' as const } : {}),
        // GAP 1 (height half) — line-height pin, recalibrated at the
        // corpus-v4.1 LINE-HEIGHT sub-boundary. The Round-4 cut pinned '18px'
        // here: back then the browser-ref forced NO font, and Chromium's
        // default-SERIF `<p>` line box was ~18px @16px while our forced Inter
        // `normal` box was ~20px — the 2px COMPOUNDED down a 10-bar test and
        // collapsed the edge-phase-sensitive SSIM. From v4.1 the ref pins the
        // harness Inter face AND an explicit `line-height: 1.25`
        // (capture-browser-ref.mjs REF_LINE_HEIGHT — 20px @16px, Chromium's
        // measured natural Inter rhythm), so the OLD 18px calibration became
        // the divergence: ref paragraphs advanced 36px top-to-top, ours 34px,
        // re-accumulating 2px per bar (the whole css-color/css-break collapse
        // of the first v4.1 run). The value itself is unchanged — what changed
        // in wave-36 (lane M4) is WHERE it comes from when the IR declares none.
        //
        // WAVE-36 CORRECTION — the fallback is `inherit`, not a re-stated
        // '1.25'. The v4.1 cut re-stated the literal here so "the span stays
        // pinned even if an intermediate ancestor ever grows a line-height".
        // That defence is precisely backwards: line-height is an INHERITED
        // property, the ref pins it at ZERO specificity on `:where(html)`
        // (capture-browser-ref.mjs) exactly so an author declaration one level
        // up wins for every descendant — and a re-statement on the innermost
        // text span can never lose, so it clobbered every ancestor-declared
        // line-height in the corpus. MEASURED on
        // css/css-overflow/line-clamp/line-clamp-009: `.clamp { font: 16px/32px
        // serif }` sits on the clamp component, the text lives in a CHILD
        // component with no declaration of its own, so `irLineHeight` was
        // undefined and this pin forced 16 x 1.25 = 20px. Capture yellow box
        // 80px (4 x 20) vs ref 128px (4 x 32) — the clamp COUNT and the
        // ellipsis were already correct; only the rhythm was wrong. 53 of the
        // 57 failing line-clamp cells in the wave-35 web map declare a
        // line-height (28 of them with no `line-clamp: auto` involvement).
        //
        // `inherit` keeps the span's declaration (nothing is "detached") while
        // resolving through the real cascade: an ancestor component's inline
        // line-height when there is one, otherwise index.html's wpt-stage rule
        // (`body.wpt-composed-mode #root { line-height: 1.25 }`), which is the
        // SAME unitless ref number and the same shape the ref uses. Bare text
        // with no ancestor declaration is therefore byte-identical to before.
        //   • Composed WPT mode ONLY — the per-component `?wpt=1` path and the
        //     327-pair baseline never set WPT_COMPOSED_MODE, so both are byte-
        //     identical to before.
        //   • DEFER to an IR-declared line-height (irLineHeight) FIRST: a
        //     component that declares its own keeps it without a cascade round
        //     trip; everything else inherits.
        ...(WPT_COMPOSED_MODE ? { lineHeight: irLineHeight ?? 'inherit' } : {}),
        // fontSize/fontWeight/letterSpacing/textTransform/etc. all
        // inherit by default — don't set them explicitly. Colour is the
        // exception: we drive it from bg luminance to match iOS.
        color,
        textAlign: 'inherit',
        // Wave 21 (lane TEXTDECOR, B-RC7) — `word-break: break-word` is a
        // HARNESS default (it keeps long placeholder labels inside their
        // cards on the 327-pair stage), NOT a CSS initial value: real CSS
        // gives an unbreakable run (no UAX #14 soft-wrap opportunity) ONE
        // overflowing line. The Chromium browser-refs for
        // text-decoration-dotted-001/002 keep 'fooשלוםbaz' / 'foobarbaz'
        // @92px on a single line overflowing the 390px canvas (the canvas
        // clips it), while this hardcoded break-word wrapped the run
        // mid-glyph and sank both tests. Composed WPT capture therefore
        // drops the harness default and inherits the spec initial
        // (`word-break: normal`) — an IR-declared WordBreak still lands
        // via the style engine's own declaration, which wins over this
        // absent default. Per-component `?wpt=1` and the 327 baseline
        // never set WPT_COMPOSED_MODE → byte-identical there. Native
        // twins: Compose gates softWrap, iOS gates fixedSize(horizontal:)
        // on the same unbreakable-run predicate (DecorationOps twins).
        ...(WPT_COMPOSED_MODE ? {} : { wordBreak: 'break-word' as const }),
      }}
    >
      {blockLayout !== null ? (
        // BLOCK-LABEL branch — the label as the shared block font: one
        // 1x1 px <rect> per set atlas bit, integer coordinates only, so
        // the same label rasterizes byte-identically on all 3 platforms.
        // An empty layout (empty name, or truncation ate every char)
        // renders nothing — same visual as the WPT suppression branch.
        blockLayout.rects.length > 0 ? (
          <svg
            // Explicit px attributes sized to the TIGHT truncated block —
            // the svg must never stretch, or the 1px bit grid resamples.
            width={blockLayout.width}
            height={blockLayout.height}
            // Keep the original label string reachable for a11y/tooling
            // (the rects carry no text content the DOM could expose).
            role="img"
            aria-label={visibleText}
            // Integer-grid rendering: no antialiasing — the whole point.
            shapeRendering="crispEdges"
            // Shared-spec pinned fill (the light label color); set once
            // here and inherited by every child <rect>.
            fill={BLOCK_LABEL_FILL}
            // Absolutely positioned inside the 0-height relative wrapper:
            // the shared-spec (8, 6) origin with ZERO flow footprint (see
            // the wrapper-span comment — in-flow labels made auto-heights
            // diverge across platforms). display:block kills the inline
            // baseline gap.
            style={{ display: 'block', position: 'absolute', left: BLOCK_LABEL_ORIGIN_X, top: BLOCK_LABEL_ORIGIN_Y }}
          >
            {blockLayout.rects.map((r, i) => (
              // One filled 1x1 px rect per set bit — block-local coords;
              // the index key is stable because the layout is pure.
              <rect key={i} x={r.x} y={r.y} width={1} height={1} />
            ))}
          </svg>
        ) : null
      ) : (
        // TEXT / suppression branches — byte-identical to before: real IR
        // text renders as glyphs, WPT_MODE renders the empty string.
        visibleText
      )}
    </span>
  );
}

/**
 * Render a list of composed nodes (e.g. the root forest of a document —
 * this is exactly how a Mode B zero-slot doc renders: a flat root list).
 */
interface ComponentListProps {
  nodes: ComposedNode[];
}

export function ComponentList({ nodes }: ComponentListProps) {
  return (
    <>
      {nodes.map((node, index) => (
        <ComponentRenderer key={node.component.id || index} node={node} />
      ))}
    </>
  );
}

export default ComponentRenderer;
