/**
 * ComposedCaptureGallery — the WPT COMPOSED capture mode (`?wptComposed=1`).
 *
 * WHY THIS EXISTS
 * ---------------
 * TITAN diffs each runtime's render of a WPT test against a Chromium render
 * of the WPT *reference* page — one COMPOSED image at 390px wide, WHITE
 * background (the corpus-v4 canvas), 16px padding, natural height
 * (tools/titan/capture-browser-ref.mjs).
 *
 * The legacy WPT web path (CaptureGallery + `?wpt=1`) captures each IR
 * component of a test on its OWN canvas (one PNG per root component), and
 * tools/titan/inject-wpt-block.mjs STITCHES a test's per-component PNGs
 * vertically before diffing. That vertical stitch does NOT reproduce the
 * reference page's real layout — bar heights, inter-element gaps, vertical
 * positions, and the ref's 16px body padding are all lost — so a MULTI-
 * component test scores low even when every component renders correctly
 * (e.g. css-color/background-color-hsl-001: 10 correct green bars, but the
 * stitched SSIM sits at ~0.91).
 *
 * This gallery fixes the geometry: it groups the loaded combined fixture's
 * components into per-test IR documents and renders EACH test's components
 * COMPOSED on ONE canvas — exactly the way a browser lays the reference
 * page out — then the capture script writes ONE PNG per test
 * (`<safe(testKey)>.png`) which inject-wpt-block diffs DIRECTLY against the
 * ref (no stitch). The canvas framing mirrors capture-browser-ref.mjs so
 * the two images are pixel-comparable.
 *
 * WHY WE MIRROR DocumentRenderer INSTEAD OF INSTANTIATING N OF THEM
 * ----------------------------------------------------------------
 * The composition itself is exactly what @style-converter/web's
 * DocumentRenderer does: `composeTree(doc)` → the flat root forest in
 * sibling order (spec 03) → one RootErrorBoundary per root. We reuse the
 * package's `composeTree` (the same function DocumentRenderer calls) and
 * `RootErrorBoundary`, and render each root with the harness's calibrated
 * `ComponentRenderer` (which carries HARNESS_OPTIONS — the WPT placeholder
 * suppression, sizing calibration, and sourceTag allowlist that the
 * per-component WPT path also uses, so the ONLY thing that changes between
 * the two paths is the composition geometry, never the per-node render).
 *
 * We do NOT instantiate the package DocumentRenderer component here because
 * it calls useDocumentRules, and that hook owns a SINGLE managed <style>
 * element per Document (MANAGED_STYLE_ID) — its own doc comment states
 * "render at most one IR document per page/Document at a time". This
 * gallery renders ~100 per-test documents on ONE page simultaneously (the
 * capture takes a single full-page screenshot), so N DocumentRenderers
 * would clobber each other's rules and tear them down on unmount. Instead
 * we rely on App.tsx's global `useDynamicRules(document)`, which mounts the
 * whole combined document's rule superset once — covering every per-test
 * doc's selector/media/@keyframes rules from a single mount.
 */

import React from 'react';
import type { IRComponent, IRDocument } from '@style-converter/web/core/ir/IRModels';
import { RootErrorBoundary } from '@style-converter/web/renderer/RootErrorBoundary';
// buildStyles is the SAME engine entry point NodeRenderer uses to resolve a
// component's inline CSS — reused here (GAP 2) to derive the document body's
// background color byte-identically to how it would render, so the composed
// canvas honors a page-level background exactly as the runtime paints it.
import { buildStyles } from '@style-converter/web/core/renderer/StyleBuilder';
// wave-49 lane A4 — the DOCUMENT-ELEMENT clip (css-masking-1 §5). The
// resolver lives in the runtime's clip category next to the extractor it
// hops through; this canvas only decides WHERE to spend it. See
// RootClipPathResolver's banner for the measurement.
import { resolveRootClipPath } from '@style-converter/web/engine/effects/clip/RootClipPathResolver';
import { composeTree } from '../sdui/Composer';
import { ComponentRenderer } from '../sdui/ComponentRenderer';

/**
 * The pipeline's default composed-canvas background — the WHITE the ref
 * canvas (capture-browser-ref.mjs's CANVAS_BG) paints on `:where(html,body)`
 * since the corpus-v4 white-canvas boundary (WPT reftests are authored
 * against the spec-default white page; white ink must vanish into it on
 * both sides of the diff — see wptCanvasStyle in CaptureGallery.tsx for
 * the full rationale). Used as the fallback when a test's document
 * declares no body background; an author `body { background }` still wins
 * (resolveCanvasBackground below), exactly as it beats the ref's
 * zero-specificity `:where()` injection.
 */
const CANVAS_BG_DEFAULT = '#FFFFFF';

/**
 * GAP 2 — BODY/ROOT BACKGROUND PROPAGATION. Resolve the background color the
 * composed canvas should paint for one per-test document.
 *
 * WHY: capture-browser-ref.mjs frames every reference page with a
 * ZERO-SPECIFICITY `:where(html, body) { background: #1A1A2E }`. Per CSS
 * Selectors L4, `:where()` contributes 0 specificity, so a reference that sets
 * its OWN `body { background: … }` (specificity 0,0,1) WINS and paints the
 * whole page that color (its `min-height:100vh` body fills the viewport).
 * css-color/a98rgb-003 is the canonical case — its ref is a full-page GREY.
 *
 * Our composed canvas hardcoded #1A1A2E, so it ignored that page background
 * and diffed a dark canvas against a grey ref (SSIM ≈ 0.38). The reader
 * captures a `body { background }` as a component tagged `meta.role:
 * 'body-root'` carrying a `BackgroundColor` property. We find that component
 * and run its properties through the same `buildStyles` engine the renderer
 * uses, then read the resolved `backgroundColor` — mirroring the ref's
 * "author body background wins over the zero-specificity default" exactly.
 * When there is no body-root, or it declares no background, we fall back to
 * the #1A1A2E default so every other test is byte-identical to before.
 *
 * wave-27 A-RC1 — THE CONTAINMENT GATE. The propagation above was
 * UNCONDITIONAL, and css-backgrounds-3 §2.11.2 says it must not be: the
 * root/body background is propagated to the canvas only while that element is
 * ON the propagation path, and css-contain-2 §2 removes it from that path
 * the moment it has ANY containment ("The background of the root element or
 * the body element … is not propagated if [it] has containment"). A contained
 * body paints its background on its OWN box and the canvas keeps the UA
 * default. MEASURED on the wave-27 gate: css-contain/contain-body-bg-001..004
 * (`body { background: red; contain: layout|paint|size|style }`, whose refs
 * are pure white — "Test passes if there is no red") each scored 0.62 against
 * the ref, our whole capture flooded red. Gating on
 * [bodyRootHasContainment] restores the ref's reading: the body's own 300×200
 * red box, fully covered by the test's own white 300×200 `<p>`.
 */
export function resolveCanvasBackground(doc: IRDocument): string {
  // The IR marks the document body with meta.role: 'body-root' (see the
  // per-test IR docs). First such component wins — a document has one body.
  const bodyRoot = doc.components.find((c) => c.meta?.role === 'body-root');
  if (!bodyRoot) return CANVAS_BG_DEFAULT;
  // wave-27 A-RC1: containment takes the body OFF the propagation path, so
  // the canvas keeps its default and the body-root component paints its own
  // background itself (it renders as a normal box in the composed tree).
  if (bodyRootHasContainment(bodyRoot)) return CANVAS_BG_DEFAULT;
  // Resolve via the engine so the color string matches what the runtime would
  // paint (rgba(...) / color-mix(...) / etc.), never a re-implemented parser.
  const bg = buildStyles(bodyRoot.properties).backgroundColor;
  // Only override when the body-root actually declared a background; a bare
  // body-root role marker (no BackgroundColor) keeps the pipeline default.
  return typeof bg === 'string' && bg.length > 0 ? bg : CANVAS_BG_DEFAULT;
}

/**
 * wave-27 A-RC1 — does the body-root carry containment? Pure, exported, and
 * twinned 1:1 by Compose's `containmentBlocksCanvasPropagation` and SwiftUI's
 * `WPTCanvas.containmentBlocksPropagation`, so the three composed canvases can
 * never drift on this decision.
 *
 * Reads the IR `Contain` leaf, whose wire shape is the converter's
 * `ContainProperty.values` list of uppercase keywords (`["LAYOUT"]`,
 * `["STRICT"]`, `["SIZE","STYLE"]`, and `contain: none` → `["NONE"]`). A
 * single bare string is accepted too — the same defensive shape Compose's
 * `extractContainValues` already tolerates for this leaf.
 *
 * TRUE iff at least one token is a real containment keyword, i.e. anything
 * except `NONE`. css-contain-2 §2 does not grade by containment KIND: the
 * WPT family proves it, since layout / paint / size / style each block
 * propagation on their own (contain-body-bg-001..004 all match the SAME
 * all-white reference). An empty or absent list is NOT containment.
 *
 * ── THE MERGED html+body CAVEAT (read before trusting this at spec level) ──
 * The extractor's `propsForBodyRoot` merges EVERY `html` / `body` / `:root` /
 * `*` rule into ONE synthetic body-root bag, so this function cannot tell
 * `html { contain: layout }` from `body { contain: layout }` — both arrive as
 * the same `Contain` leaf on the same component. For the propagation question
 * that conflation is harmless and the corpus proves it: css-contain's
 * contain-html-bg-001..004 (containment on html) and contain-body-bg-001..004
 * (containment on body) declare the SAME expectation and match the SAME
 * reference — either element having containment removes the propagation. It
 * would matter for a document that contains one element and propagates a
 * background off the OTHER (e.g. `html { contain: layout }` +
 * `body { background: red }` where the spec still propagates from body,
 * because it is html that left the path); WPT has no such reftest, and the
 * honest fix is a wire that keeps html and body apart, not a guess here.
 */
export function bodyRootHasContainment(bodyRoot: IRComponent): boolean {
  const prop = (bodyRoot.properties as Array<{ type: string; data?: unknown }> | undefined)
    ?.find((p) => p.type === 'Contain');
  if (!prop) return false;                       // no `contain` declaration at all
  const d = prop.data;
  // Array wire (the converter's normal emission) or a bare keyword string.
  const tokens = Array.isArray(d) ? d : (typeof d === 'string' ? [d] : []);
  // Any token that is not `none` is containment; `["NONE"]` and `[]` are not.
  return tokens.some((t) => typeof t === 'string' && t.trim().toUpperCase() !== 'NONE');
}

/**
 * The five `writing-mode` keywords css-writing-modes-4 §3.1 defines. Anything
 * outside this set is not a writing mode and never reaches the canvas — the
 * resolver below refuses unknown strings rather than writing an arbitrary IR
 * value into a live style object (no-silent-fallthrough).
 */
const WRITING_MODE_KEYWORDS = new Set([
  'horizontal-tb', 'vertical-rl', 'vertical-lr', 'sideways-rl', 'sideways-lr',
]);

/**
 * wave-37 lane W6 — THE PRINCIPAL WRITING MODE (root block-axis rotation).
 *
 * WHY: css-writing-modes-4 §8 ("The Principal Writing Mode") says the
 * `writing-mode` (and `direction`) used for the ICB — and therefore for the
 * whole block-progression axis of the document — is taken from the ROOT
 * element, or, in HTML, from the first `<body>` child when it has one. A page
 * that opens `html { writing-mode: vertical-rl }` (css-logical's
 * logical-values-float-clear-2/-4) or `body { writing-mode: vertical-rl }`
 * (the 24-strong css-writing-modes/wm-propagation-body-0xx family) lays its
 * top-level blocks out as a RIGHT-TO-LEFT row of vertical columns, not a
 * top-to-bottom stack.
 *
 * WHY IT WAS LOST: the extractor already keeps the declaration — it lands on
 * the synthetic `meta.role: 'body-root'` component, exactly where the
 * background and padding propagations read from. But that component is a
 * SIBLING of the document's top-level blocks in the composed root forest (spec
 * 03 flat placement), not their parent, so its `writing-mode` styled an empty
 * zero-size box and inherited to nobody. All 89 corpus fixtures with a
 * vertical body-root were therefore laid out on a HORIZONTAL block axis.
 *
 * MEASURED (--web-only, wave-37 head, base → this change):
 *   css-logical                          pass 3/6   → 5/6
 *   css-writing-modes                    pass 69/136 → 72/136
 *   logical-values-float-clear-2         0.2254 → 0.9976
 *   logical-values-float-clear-4         0.5995 → 0.9938
 *   vrl-…-sideways-alongside-vrl-floats  0.6841 → 1.0000
 *   text-underline-position-vertical     0.9649 → 1.0000
 *   wm-propagation-body-032              0.8080 → 0.8967 — the blue square
 *     moves from the upper-LEFT to the upper-RIGHT corner the ref
 *     (block-flow-direction-025-ref.xht) shows; the family stops short of
 *     0.95 on a SECOND, unrelated defect: its message `<img width=359
 *     height=36>` reaches the IR as the extractor's 100×100 rule-less
 *     placeholder, so the bundled PNG paints crushed.
 * The four `alongside-…-floats` tests also lost their overflow ink entirely
 * (capture 390×801 with 18 500 overflow px → 390×600 with 0), i.e. the
 * rotation fixes the document HEIGHT too, not just the box order.
 *
 * THE FIX: hoist the resolved mode onto the ICB div — the box that IS the
 * ref's render viewport (see composedIcbStyle). `writing-mode` is an INHERITED
 * property, so one declaration there reproduces the ref exactly: the root
 * forest stacks along the rotated block axis and every descendant's text runs
 * inherit the vertical flow, all of it laid out by Blink's own orthogonal-flow
 * pass. Nothing is re-implemented in the harness.
 *
 * THE CONTAINMENT GATE: propagation to the viewport is conditional in exactly
 * the way the background's is, and WPT states it in the test TITLES rather
 * than leaving it to inference — css-contain/contain-body-w-m-001..004 and
 * contain-html-w-m-001..004 read "layout / paint / size / style containment on
 * body|html prevents writing-mode propagation", one per keyword, and all eight
 * match ONE reference whose orange square sits in the upper-LEFT (i.e. the
 * viewport stayed horizontal-tb). Since every containment kind blocks it
 * alone, the gate does not grade by kind: we reuse [bodyRootHasContainment] —
 * the same predicate resolveCanvasBackground gates on, inheriting its merged
 * html+body caveat verbatim. VERIFIED at the wave-37 head: all eight fixtures
 * carry `contain` on the SAME body-root bag as the `writing-mode`, so all
 * eight captures stay byte-identical (they are depth-48 gate cells that iOS
 * and Android pass at 0.9992 / 0.9891).
 *
 * Returns `undefined` when there is no body-root, when it declares no
 * writing-mode, when the declared mode is the initial `horizontal-tb` (which
 * is what the canvas already does), when containment blocks the propagation,
 * or when the value is not a css-writing-modes keyword — in every one of those
 * cases the caller writes NO `writingMode` key and the capture is byte-
 * identical to wave 36.
 */
export function resolveCanvasWritingMode(doc: IRDocument): string | undefined {
  // Same lookup rule as resolveCanvasBackground / resolveCanvasPadding — a
  // document has exactly one body, and the extractor emits one bag for it.
  const bodyRoot = doc.components.find((c) => c.meta?.role === 'body-root');
  if (!bodyRoot) return undefined;
  // Containment takes the element off the propagation path (see doc above).
  if (bodyRootHasContainment(bodyRoot)) return undefined;
  // Resolve through the SAME engine the renderer uses (WritingModeApplier),
  // so the canvas can never disagree with what a component would have got.
  const wm = buildStyles(bodyRoot.properties).writingMode;
  if (typeof wm !== 'string') return undefined;                 // not declared
  const value = wm.trim().toLowerCase();
  // Unknown keyword ⇒ refuse (no-silent-fallthrough); `horizontal-tb` ⇒ the
  // canvas default, so writing it would be a no-op with a byte-diff risk.
  if (!WRITING_MODE_KEYWORDS.has(value) || value === 'horizontal-tb') return undefined;
  return value;
}

/**
 * The two `direction` keywords css-writing-modes-4 §2.1 defines. Same
 * no-silent-fallthrough discipline as WRITING_MODE_KEYWORDS above: an IR value
 * outside this set is refused rather than written into a live style object.
 */
const DIRECTION_KEYWORDS = new Set(['ltr', 'rtl']);

/**
 * wave-38 lane N6 — THE PRINCIPAL DIRECTION (root inline-axis reversal).
 *
 * WHY: css-writing-modes-4 §8 names TWO propagated properties, not one. The
 * same sentence that hands the root's (or, in HTML, the first `<body>`'s)
 * `writing-mode` to the initial containing block hands over its `direction`:
 * "the used values of writing-mode, direction and text-orientation on the root
 * element are propagated to the viewport". Wave-37 lane W6 wired the block
 * axis (resolveCanvasWritingMode above) and left the INLINE axis unwired, so a
 * page that opens `body { direction: rtl }` still laid its ICB out left-to-
 * right.
 *
 * WHAT THAT COSTS, precisely. `direction` is inherited, and the extractor
 * already BAKES the body-root's declaration down onto every top-level body
 * child (extract-fixture.mjs ROOT_INHERITED_TRIGGER_PROPS — `direction` is on
 * that list by name), so the TEXT inside each root was already running RTL.
 * What the bake cannot deliver is the one thing only the containing block can
 * decide: WHERE a root-forest box that is narrower than the ICB is placed.
 * CSS 2.1 §10.3.3 reads the CONTAINING BLOCK's direction for an
 * over-constrained block ("if direction is rtl, margin-left is ignored"), and
 * css-align/css-flexbox read it for `justify-content`/`start`. Under an LTR
 * ICB every such box hugged the physical LEFT edge; the refs put it right.
 *
 * THE FIX, and why it is the same three lines W6 used: hoist the resolved
 * keyword onto the ICB div — the box that IS the ref's render viewport (see
 * composedIcbStyle). One inherited declaration there gives the root forest the
 * ref's containing-block direction AND reaches every descendant, all resolved
 * by Blink's own bidi/alignment pass. Nothing is re-implemented here.
 *
 * THE CONTAINMENT GATE is not an analogy this time, it is the spec's own
 * wording: css-contain-1 §2 says that when `contain` on html or body is
 * anything but `none`, "propagation of properties from the body element to
 * the initial containing block, the viewport, or the canvas background, is
 * disabled. Notably, this affects: writing-mode, direction, and
 * text-orientation" — direction is named alongside writing-mode in the same
 * clause. WPT states it in the test TITLES:
 * css-contain/contain-body-dir-001..004 and contain-html-dir-001..004 read
 * "layout|paint|size|style containment on body|html prevents direction
 * propagation", all eight matching ONE reference whose orange square sits in
 * the upper-LEFT (the viewport stayed `ltr`). We therefore reuse
 * [bodyRootHasContainment] — the identical predicate the background and
 * writing-mode propagations gate on, inheriting its merged html+body caveat
 * verbatim — and, like §2, do not grade by containment KIND.
 *
 * Returns `undefined` when there is no body-root, when it declares no
 * direction, when the declared value is the initial `ltr` (which is what the
 * canvas already does), when containment blocks the propagation, or when the
 * value is not a css-writing-modes keyword. In every one of those cases the
 * caller writes NO `direction` key and the capture is byte-identical to wave
 * 37 — which is what keeps the ~10,600-fixture corpus still.
 */
export function resolveCanvasDirection(doc: IRDocument): string | undefined {
  // Same lookup rule as the three resolvers above — one body per document, one
  // synthetic bag for it.
  const bodyRoot = doc.components.find((c) => c.meta?.role === 'body-root');
  if (!bodyRoot) return undefined;
  // css-contain-1 §2 names `direction` in the same clause as `writing-mode`.
  if (bodyRootHasContainment(bodyRoot)) return undefined;
  // Resolve through the SAME engine the renderer uses (DirectionApplier), so
  // the canvas can never disagree with what a component would have got.
  const dir = buildStyles(bodyRoot.properties).direction;
  if (typeof dir !== 'string') return undefined;                  // not declared
  const value = dir.trim().toLowerCase();
  // Unknown keyword ⇒ refuse (no-silent-fallthrough); `ltr` ⇒ the canvas
  // default, so writing it would be a no-op with a byte-diff risk.
  if (!DIRECTION_KEYWORDS.has(value) || value === 'ltr') return undefined;
  return value;
}

/**
 * The `text-align` keywords css-text-4 §7.1 defines that are SELF-CONTAINED —
 * they decide a line box's alignment from the value alone. `match-parent` is
 * deliberately absent: it resolves against the PARENT's direction, and the ICB
 * has no parent in the composed canvas, so forwarding it would be a guess.
 */
const TEXT_ALIGN_KEYWORDS = new Set([
  'left', 'right', 'center', 'justify', 'justify-all', 'start', 'end',
]);

/**
 * wave-38 lane N6 — the body-root's `text-align`, and ONLY while the canvas
 * direction is reversed. This is not a spec propagation; it repairs a HARNESS
 * artifact that reversing the inline axis makes observable.
 *
 * THE ARTIFACT. In the real document a top-level `<span>` is an INLINE-level
 * child of `<body>`, so the line box it sits in belongs to body's block box and
 * body's `text-align` decides where that line box is placed. In the flat v2
 * forest (spec 03) the body's children are SIBLINGS of the body-root component,
 * so their line box belongs to the ICB div instead — and the ICB carried no
 * `text-align` at all, i.e. the initial `start`.
 *
 * That was invisible while the ICB was `ltr`, because `start` and the corpus's
 * common `left` land in the same place. Reversing the inline axis separates
 * them, and CSS2/text/bidi-span-002 is the exact cell that proves it:
 * `body { text-align: left; direction: rtl }` with a single inline `<span>`
 * root whose `::before`/`::after` generate "(" and ")". MEASURED at this
 * wave's head, on the 42-test root-direction slice: the direction propagation
 * alone moved its ink from image x:18..25 (the ref's position, SSIM 1.0000) to
 * x:364..371 — the rtl ICB's `start` edge — for 0.9945. Reading the body's own
 * `text-align: left` onto the ICB puts it back at 1.0000 with the reordering
 * the test is actually about (in an RTL base direction the mirrored, reversed
 * "(" + ")" render as "()" again, which is why the plain-LTR ref matches).
 *
 * WHY IT IS GATED ON THE DIRECTION. The artifact is GENERAL — an LTR document
 * with `body { text-align: center }` and an inline-level root has the same hole
 * — but closing it generally would rewrite the ICB style of every fixture whose
 * body declares an alignment, corpus-wide, on zero measurement. This lane
 * measured the RTL slice, so this lane moves the RTL slice: `undefined` unless
 * [resolveCanvasDirection] already fired, which keeps the change inside the 33
 * body-root-rtl fixtures the lane owns. The general widening is a follow-up
 * with its own A/B, recorded as such rather than smuggled in here.
 *
 * (The bake is not an alternative route: extract-fixture's
 * ROOT_INHERITED_TRIGGER_PROPS already copies `text-align` onto every top-level
 * child, but `text-align` has no effect on an INLINE box — it aligns the line
 * boxes of a BLOCK container — so the copy lands on the span and does nothing.
 * Only the box that establishes the line box can answer, and here that is the
 * ICB.)
 */
export function resolveCanvasTextAlign(doc: IRDocument): string | undefined {
  // Self-gating: silent for every document whose inline axis we did not
  // reverse, which is what bounds the blast radius to the measured slice.
  if (resolveCanvasDirection(doc) === undefined) return undefined;
  const bodyRoot = doc.components.find((c) => c.meta?.role === 'body-root');
  if (!bodyRoot) return undefined;                    // unreachable via the gate
  // Same engine hop as every resolver above (TextAlignApplier).
  const ta = buildStyles(bodyRoot.properties).textAlign;
  if (typeof ta !== 'string') return undefined;                   // not declared
  const value = ta.trim().toLowerCase();
  // Unknown or context-dependent keyword ⇒ refuse (no-silent-fallthrough).
  if (!TEXT_ALIGN_KEYWORDS.has(value)) return undefined;
  // `start` is the ICB's initial value — writing it would be a no-op with a
  // byte-diff risk, exactly like `horizontal-tb` / `ltr` above.
  if (value === 'start') return undefined;
  return value;
}

/**
 * The composed canvas's image-space FRAME — capture-browser-ref.mjs's
 * CANVAS_PAD_PX. Since wave-25 CAL-RC1 the ref renders UNPADDED at 358 wide
 * and this 16px frame is memcpy'd around the finished PNG (padPngBuffer), so
 * it is unconditional: no author `body { padding }` can cancel an image-space
 * translation, it can only add an inset INSIDE the frame.
 *
 * The natives read the same number from their runtimes (Compose
 * `WPT_CANVAS_FRAME_DP`, SwiftUI `WPTCanvas.canvasFramePx`).
 */
export const CANVAS_FRAME_PX = 16;

/**
 * The ref's RENDER VIEWPORT — `capture-browser-ref.mjs`'s REF_RENDER_WIDTH
 * (390 − 2×16 = 358) and REF_RENDER_MIN_HEIGHT (600 − 2×16 = 568). This is
 * the INITIAL CONTAINING BLOCK: what `position: absolute`/`fixed` boxes with
 * no positioned ancestor anchor against (css-position-3 §3.1/§3.2), and what
 * `100vw`/`100vh` resolve to in the ref. The inner canvas div below IS this
 * box, which is how the web canvas reproduces the ref's geometry.
 */
export const ICB_WIDTH_PX = 390 - 2 * CANVAS_FRAME_PX;
export const ICB_MIN_HEIGHT_PX = 600 - 2 * CANVAS_FRAME_PX;

/**
 * The pipeline's default composed-canvas pad — the FRAME alone, i.e. what a
 * document whose body-root declares no padding gets. Kept at 16 so every such
 * capture is byte-identical to wave 24.
 */
const CANVAS_PAD_DEFAULT = CANVAS_FRAME_PX;

/** Per-side canvas pad in CSS px. */
export interface CanvasPadding {
  top: number; right: number; bottom: number; left: number;
}

/**
 * wave-24 B-RC5 — BODY/ROOT PADDING PROPAGATION. The exact twin of
 * resolveCanvasBackground above, for the other half of the ref's
 * zero-specificity body frame.
 *
 * WHY (wave 24): capture-browser-ref.mjs used to inject
 * `:where(body) { padding: 16px }`. `:where()` contributes ZERO specificity
 * (CSS Selectors L4 §17), so a ref that declared its OWN `body { padding: 0 }`
 * (specificity 0,0,1) WON and painted with no body pad at all. This canvas
 * hardcoded 16px, so every such test's whole render was offset (+16, +16)
 * against its ref — MEASURED as the ENTIRE divergence of
 * css-masking/clip-path-circle-007, whose test and ref both open with
 * `body, div { padding: 0; margin: 0 }`.
 *
 * WHY IT CHANGED (wave 25 round 3): at CAL-RC1 the ref stopped injecting a
 * body padding at all — it renders at 358 wide with `padding: 0` and the
 * 16px frame is applied to the finished PNG in IMAGE space. So the two halves
 * wave 24 conflated have SPLIT: the FRAME ([CANVAS_FRAME_PX]) is
 * unconditional, and the AUTHOR's body padding is an ADDITIONAL inset inside
 * it, defaulting to ZERO. Each side therefore resolves to `frame + declared`:
 * nothing declared → 16 (unchanged); `padding: 0` → 16 (wave 24 gave 0 — the
 * stale calibration this round repairs); `padding: 40px` → 56.
 *
 * The canvas SPENDS the two halves differently — the frame on the outer div,
 * the author part on the inner ICB div (see ComposedTestCanvas) — but this
 * resolver reports the SUM, because that sum is the cross-platform contract
 * the two native twins (`resolveComposedCanvasPadding`, `resolvedPadding`)
 * also return, and keeping the three functions comparable is what stops them
 * drifting.
 *
 * The resolution is PER SIDE, because the cascade is per-longhand: a ref
 * declaring only `padding-left: 0` leaves the other three at the bare frame.
 * Values are resolved through the same `buildStyles` engine the renderer
 * uses (never a re-implemented parser), and only CONCRETE px are honored —
 * a runtime-dependent length (`1em`, `%`, `calc()`, which the converter
 * emits as unresolved) has no absolute answer here, so that side keeps the
 * 16px default rather than silently guessing.
 *
 * No body-root, or a body-root declaring no padding ⇒ all four sides stay at
 * the bare frame (16), so those captures are byte-identical to before.
 */
export function resolveCanvasPadding(doc: IRDocument): CanvasPadding {
  const pad: CanvasPadding = {
    top: CANVAS_PAD_DEFAULT, right: CANVAS_PAD_DEFAULT,
    bottom: CANVAS_PAD_DEFAULT, left: CANVAS_PAD_DEFAULT,
  };
  // Same lookup rule as resolveCanvasBackground — a document has one body.
  const bodyRoot = doc.components.find((c) => c.meta?.role === 'body-root');
  if (!bodyRoot) return pad;
  // One engine pass; the applier emits paddingTop/… as CSS length strings.
  const styles = buildStyles(bodyRoot.properties) as Record<string, unknown>;
  const sides: Array<[keyof CanvasPadding, string]> = [
    ['top', 'paddingTop'], ['right', 'paddingRight'],
    ['bottom', 'paddingBottom'], ['left', 'paddingLeft'],
  ];
  // Wave 24 (skeptic B1) — the three composed canvases must honor the SAME
  // IR leaf shapes. The natives read the raw leaf ({px:N} or the wrapped
  // {original:{px:N}} pxFallback); buildStyles here re-emits the AUTHOR's
  // unit for wrapped leaves (correct for the engine, wrong for the canvas
  // frame, which wants the resolved px). So: engine string first, then the
  // raw-leaf fallback identical to the native resolvers.
  const irSides: Record<keyof CanvasPadding, string> = {
    top: 'PaddingTop', right: 'PaddingRight',
    bottom: 'PaddingBottom', left: 'PaddingLeft',
  };
  const leafPx = (name: string): number | null => {
    const prop = (bodyRoot.properties as Array<{ type: string; data?: unknown }>)
      .find((pr) => pr.type === name);
    const d = prop?.data as { px?: unknown; original?: { px?: unknown } } | undefined;
    if (typeof d?.px === 'number') return d.px;
    if (typeof d?.original?.px === 'number') return d.original.px;
    return null;                                     // unresolved (em/%/calc)
  };
  for (const [side, key] of sides) {
    const raw = styles[key];
    let px: number | null = null;
    if (typeof raw === 'string') {
      // Concrete px only (see doc): anything else falls through to the leaf.
      const m = /^(-?\d+(?:\.\d+)?)px$/.exec(raw.trim());
      if (m) px = parseFloat(m[1]);
    }
    if (px === null) px = leafPx(irSides[side]);     // the native-parity path
    if (px === null) continue;                       // side not resolvable
    // CSS 2.1 §8.4 forbids negative padding; clamp the AUTHOR term so a
    // malformed IR can never pull content OUTSIDE the canvas frame — then
    // stack it ON the frame, which no author rule can cancel any more.
    pad[side] = CANVAS_FRAME_PX + Math.max(0, px);
  }
  return pad;
}

interface ComposedCaptureGalleryProps {
  /** The decoded COMBINED IR document (every WPT test's components, flat). */
  document: IRDocument;
  /**
   * B-RC3 isolated-capture filter (`?only=<testKey>` via App.tsx): when
   * set, mount ONLY the test whose key equals this value — a fresh page
   * holding a single composed canvas, where headless Chromium paints the
   * spanner+abspos family correctly (the tall multi-canvas page mis-paints
   * it ~2312px off despite correct geometry). Undefined ⇒ the full gallery,
   * byte-identical to the pre-B-RC3 render.
   */
  only?: string;
}

/**
 * The test key of a ROOT component's name — ported VERBATIM from
 * tools/titan/split-combined-ir.mjs's `rootTestKey` (kept in lock-step by
 * this citation; the harness can't cleanly import a tools/ .mjs under
 * vite+TS). build-combined-fixture.mjs prefixes only ROOTS as
 * `wpt__<section>__<stem>__<idx>`, so the test key is the root name MINUS
 * the trailing `__<idx>` segment. wave-21 collision fix: this used to take
 * the FIRST THREE `__`-delimited segments, which was only correct while
 * stems never contained `__` — the subdir-encoded stems introduced by the
 * fixture-collision fix (safe-name.mjs fixtureStem, e.g.
 * `flexbox__monolithic-overflow-001.tentative`) contain `__` by design, and
 * slice(0, 3) would truncate them to the bare subdir name, merging every
 * test in that subdir into one bogus composed canvas. Stripping the one
 * trailing child-index segment is exact for BOTH shapes and byte-identical
 * to the old rule for every `__`-free stem. Non-`wpt__` roots (defensive)
 * become their own group under their raw name.
 */
function rootTestKey(rootName: string | undefined): string {
  const parts = String(rootName ?? '').split('__');
  if (parts[0] === 'wpt' && parts.length >= 4) return parts.slice(0, -1).join('__');
  return String(rootName ?? '');
}

/**
 * Resolve the test key for ANY component — ported VERBATIM from
 * tools/titan/split-combined-ir.mjs's `testKeyOf`. Flattened CHILD
 * components keep their raw extracted names (only the root carries the
 * `wpt__` prefix), so a child is grouped with its test ONLY by walking its
 * `slot.parent` chain up to the in-document root, then keying on the root's
 * name. A chain that leaves the document or cycles stops at the last
 * in-document component (treated as the root), so no component is dropped.
 */
function testKeyOf(component: IRComponent, byId: Map<string, IRComponent>): string {
  let cur: IRComponent | undefined = component;
  const seen = new Set<string>();
  // Climb while the current node has an in-document slot parent.
  while (cur?.slot?.parent && byId.has(cur.slot.parent) && !seen.has(cur.id)) {
    seen.add(cur.id);
    cur = byId.get(cur.slot.parent);
  }
  return rootTestKey(cur?.name ?? cur?.id ?? '');
}

/**
 * Group a combined IR document into per-test documents — the browser-side
 * twin of splitCombinedIr in tools/titan/split-combined-ir.mjs. First-seen
 * group order is preserved so the on-page canvas order is deterministic and
 * matches the source document order. Each emitted doc carries the v2
 * version pair, that test's components, and the document-level keyframes
 * block when the source had one (it is document-scoped, so every split doc
 * that might reference an animation keeps access to the full set).
 */
function groupByTest(combined: IRDocument): { key: string; doc: IRDocument }[] {
  const byId = new Map<string, IRComponent>();
  for (const c of combined.components) if (c.id) byId.set(c.id, c);
  const groups = new Map<string, IRComponent[]>();
  for (const c of combined.components) {
    const key = testKeyOf(c, byId);
    if (!groups.has(key)) groups.set(key, []);
    groups.get(key)!.push(c);
  }
  const out: { key: string; doc: IRDocument }[] = [];
  for (const [key, components] of groups) {
    const doc: IRDocument = { irVersion: 2, minReaderVersion: 2, components };
    if (combined.keyframes) doc.keyframes = combined.keyframes;
    out.push({ key, doc });
  }
  return out;
}

export function ComposedCaptureGallery({ document, only }: ComposedCaptureGalleryProps) {
  // Grouping is pure per document — memoise on document identity (and on
  // the isolated-capture filter, which narrows the grouped list). The
  // filter compares FULL test keys (exact equality, not prefix), so
  // `wpt__a__b` can never accidentally match `wpt__a__b-2`. A key that
  // matches nothing yields ZERO canvases and a `data-capture-ready="0"`
  // sentinel — the isolated driver asserts exactly 1 and fails loudly,
  // per the no-silent-fallthrough contract.
  const tests = React.useMemo(() => {
    const all = groupByTest(document);
    return only != null ? all.filter((t) => t.key === only) : all;
  }, [document, only]);
  return (
    <div style={containerStyle}>
      {tests.map(({ key, doc }, index) => (
        <ComposedTestCanvas key={key || index} testKey={key} doc={doc} index={index} />
      ))}
      {/* Sentinel so Puppeteer can tell when the full list has rendered —
          same contract capture-screenshots.mjs waits on. Value = the number
          of composed canvases (one per test), which the capture script
          asserts against the live `[data-capture-canvas]` count. */}
      <div data-capture-ready={tests.length} style={{ height: 0, overflow: 'hidden' }} />
    </div>
  );
}

interface ComposedTestCanvasProps {
  /** WPT test key (`wpt__<section>__<stem>`) — becomes the PNG filename. */
  testKey: string;
  /** The per-test IR document (that test's components only). */
  doc: IRDocument;
  index: number;
}

/**
 * One composed capture surface = one WPT test's whole reference-page render.
 * Mirrors DocumentRenderer.ts's body: compose the per-test doc into its
 * root forest, then render each root (in flat sibling order) inside its own
 * RootErrorBoundary via the calibrated harness ComponentRenderer. One
 * malformed component fails alone as a visible data-render-error box instead
 * of unmounting the canvas and starving the data-capture-ready sentinel.
 */
function ComposedTestCanvas({ testKey, doc, index }: ComposedTestCanvasProps) {
  // Composition is pure per document — memoise on identity (same as
  // DocumentRenderer's `useMemo(() => composeTree(doc), [doc])`).
  const roots = React.useMemo(() => composeTree(doc), [doc]);
  // GAP 2: the canvas background honors the document's body-root background
  // (falling back to #1A1A2E) so a full-page-colored ref (e.g. a98rgb-003's
  // grey page) is matched instead of diffed against a dark canvas. Pure per
  // document — memoise on identity alongside the composition above.
  const canvasBackground = React.useMemo(() => resolveCanvasBackground(doc), [doc]);
  // wave-24 B-RC5: the canvas pad honors the document's body-root padding
  // (falling back to 16px per side) so a ref that zeroes its body pad is
  // matched instead of diffed at a (+16,+16) offset — clip-path-circle-007's
  // whole divergence. Pure per document, memoised like the background.
  const canvasPadding = React.useMemo(() => resolveCanvasPadding(doc), [doc]);
  // wave-37 W6: the PRINCIPAL WRITING MODE (css-writing-modes-4 §8). Pure per
  // document like the two resolvers above — memoised on the same identity.
  // `undefined` for every horizontal document, which is what keeps the rest of
  // the corpus byte-identical (the style key is then never written).
  const canvasWritingMode = React.useMemo(() => resolveCanvasWritingMode(doc), [doc]);
  // wave-38 N6: the PRINCIPAL DIRECTION — the inline-axis half of the same
  // css-writing-modes-4 §8 sentence W6 wired the block axis from. Pure per
  // document like the resolvers above; `undefined` for every LTR document,
  // which is what keeps the rest of the corpus byte-identical.
  const canvasDirection = React.useMemo(() => resolveCanvasDirection(doc), [doc]);
  // wave-38 N6: the body's own `text-align`, consulted ONLY when the line above
  // reversed the inline axis (resolveCanvasTextAlign gates on it internally).
  const canvasTextAlign = React.useMemo(() => resolveCanvasTextAlign(doc), [doc]);
  // wave-49 A4: the DOCUMENT ELEMENT's own `clip-path`. `undefined` for every
  // document whose merged root declares none — 1433 of the corpus's 1435 —
  // which is what keeps the rest of the corpus byte-identical: no style key
  // is written and the background stays exactly where wave-25 put it.
  const canvasClipPath = React.useMemo(() => resolveRootClipPath(doc), [doc]);
  return (
    <div
      data-capture-canvas
      data-capture-index={index}
      // The capture id/name IS the test key: capture-screenshots.mjs reads
      // data-capture-name and (in composed mode) writes `<safe(name)>.png`,
      // which is exactly what inject-wpt-block.mjs's composed path globs for.
      data-capture-id={testKey}
      data-capture-name={testKey}
      // Spread the shared frame first, then supply `background` and the four
      // `padding-*` sides. wave-25 round 3: the OUTER div carries the CANVAS
      // FRAME only — the ref's image-space pad, which is the same 16px on
      // every document — while the author's own body padding rides the inner
      // ICB div below. composedCanvasStyle deliberately carries NO `padding`
      // key so the shorthand and these longhands can never both be serialised
      // into one style object (React warns on that mix, and the winner would
      // depend on key order).
      style={{
        ...composedCanvasStyle,
        // wave-49 A4 — WHERE the propagated root background is painted.
        // Normally it is HERE, on the framed outer surface (wave-24 B-RC5).
        // But when the document element carries a `clip-path`, css-masking-1
        // §5 puts that background INSIDE the root's clip — the fxtf
        // compositing §rootgroup/§pagebackdrop chain the WPT test links, and
        // its assert states it outright ("Clip-path on the document element
        // applies to the root background"). The clip's coordinates are the
        // ROOT element's border box, i.e. the ICB div below, so the
        // background moves onto that same box and is clipped with it; this
        // outer surface then paints the page backdrop, which is the canvas
        // default. MEASURED: with the background left here the frame band
        // would keep painting red outside the clip, where the Chromium ref
        // (clip-path-document-element[-will-change]) is pure white.
        background: canvasClipPath ? CANVAS_BG_DEFAULT : canvasBackground,
        paddingTop: `${CANVAS_FRAME_PX}px`,
        paddingRight: `${CANVAS_FRAME_PX}px`,
        paddingBottom: `${CANVAS_FRAME_PX}px`,
        paddingLeft: `${CANVAS_FRAME_PX}px`,
      }}
    >
      {/* wave-25 round 3 — THE INITIAL CONTAINING BLOCK.
          MEASURED problem: `position: relative` + `transform` used to sit on
          the OUTER div, so an abspos/fixed child anchored at that div's
          PADDING box — the canvas corner. A probe of the live canvas CSS put
          `left:100px` at canvas x=100 and `right:0;bottom:0` at (380,590),
          i.e. flush with the IMAGE edge, while in-flow content sat at
          (16,16). That matched the pre-CAL-RC1 refs (a CSS body pad moves
          in-flow content only) and is 16px off the new ones.
          FIX: the positioning/containing-block role moves to this inner div,
          which IS the ref's render viewport — 358 x 568 minimum, offset
          (16,16) by the outer frame. The same probe then measures
          `left:100px` at (116,16), fixed at (16,16) and `right:0;bottom:0`
          at (364,574) — flush with the CONTENT edge, frame intact: exactly
          what the image-space-framed ref raster contains.
          The AUTHOR body padding rides here too (inside the frame, like the
          ref's own `body { padding }` inside its 358-wide viewport), derived
          by removing the frame term the resolver added — see
          resolveCanvasPadding for why that resolver reports the sum. */}
      <div
        data-capture-icb
        style={{
          ...composedIcbStyle,
          paddingTop: `${canvasPadding.top - CANVAS_FRAME_PX}px`,
          paddingRight: `${canvasPadding.right - CANVAS_FRAME_PX}px`,
          paddingBottom: `${canvasPadding.bottom - CANVAS_FRAME_PX}px`,
          paddingLeft: `${canvasPadding.left - CANVAS_FRAME_PX}px`,
          // wave-37 W6 — the principal writing mode rides the ICB, because the
          // ICB IS the ref's render viewport and `writing-mode` is inherited:
          // one declaration here rotates the block-progression axis for the
          // whole root forest and every descendant, exactly as the ref page's
          // `html`/`body` declaration does. Spread LAST and conditionally, so a
          // horizontal document writes no key at all and stays byte-identical.
          ...(canvasWritingMode ? { writingMode: canvasWritingMode as React.CSSProperties['writingMode'] } : {}),
          // wave-38 N6 — the principal DIRECTION rides the ICB for the same
          // two reasons the writing mode does: the ICB IS the ref's render
          // viewport (so it is the containing block whose direction CSS 2.1
          // §10.3.3 reads when placing an over-constrained root-forest box),
          // and `direction` is inherited (so one declaration reaches every
          // descendant's bidi resolution). Spread LAST and conditionally, so an
          // LTR document writes no key at all and stays byte-identical.
          ...(canvasDirection ? { direction: canvasDirection as React.CSSProperties['direction'] } : {}),
          // wave-38 N6 — the body's `text-align` rides the ICB alongside the
          // reversed direction, because in the flat forest the ICB is the box
          // that establishes the line box a top-level INLINE root sits in.
          // Never written on its own: the resolver is silent unless the
          // direction fired, so no LTR capture can move.
          ...(canvasTextAlign ? { textAlign: canvasTextAlign as React.CSSProperties['textAlign'] } : {}),
          // wave-49 A4 — the DOCUMENT-ELEMENT clip rides the ICB for the
          // same reason the writing mode and direction do: this div IS the
          // root element's rendering surface (the ref's render viewport), so
          // its BORDER BOX is the reference box `clip-path`'s lengths are
          // measured from — polygon(50px 50px …) lands at image (66,66),
          // exactly where the Chromium ref draws it. The root background
          // travels with it (see the outer div above) so the one clip covers
          // the background AND the whole root forest, which in the flat v2
          // wire lives inside this div as the root's siblings-turned-
          // children. Spread LAST and conditionally, so a document with no
          // root clip writes no key at all and stays byte-identical.
          ...(canvasClipPath
            ? { background: canvasBackground, clipPath: canvasClipPath }
            : {}),
        }}
      >
        {roots.map((root, i) => (
          <RootErrorBoundary key={root.component.id || i} componentId={root.component.id}>
            <ComponentRenderer node={root} />
          </RootErrorBoundary>
        ))}
      </div>
    </div>
  );
}

/**
 * Flat vertical list of composed canvases, WHITE (the corpus-v4 canvas —
 * this gallery is WPT-composed-only) so any sub-pixel bleed between
 * per-test crops is invisible against the white canvases. Same shape as
 * CaptureGallery's container so the single-full-page-screenshot +
 * per-canvas crop pipeline in capture-screenshots.mjs works unchanged.
 */
const containerStyle: React.CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  alignItems: 'flex-start',
  background: '#FFFFFF',
  margin: 0,
  padding: 0,
};

/**
 * Composed capture canvas — framed to match capture-browser-ref.mjs EXACTLY
 * so a composed PNG is directly diffable against the Chromium browser-ref:
 *   - width 390px            (CANVAS_WIDTH in capture-browser-ref.mjs)
 *   - background WHITE       (CANVAS_BG — the corpus-v4 white canvas the ref
 *                             sets on `:where(html,body)`; white ink vanishes
 *                             into it on BOTH sides, restoring the reftest
 *                             camouflage the dark stage broke)
 *   - padding 16px per side  (CANVAS_FRAME_PX — the ref's image-space frame;
 *                             box-sizing:border-box so the content box is the
 *                             358px viewport the ref is RENDERED at. wave-25
 *                             round 3: unconditional — an author
 *                             `body { padding }` no longer replaces it, it
 *                             insets FURTHER on the inner ICB div. See
 *                             resolveCanvasPadding + composedIcbStyle.)
 *   - min-height 600px       (the ref's `min-height:100vh` floors documentHeight
 *                             at 600 — capture-browser-ref.mjs's docHeight max;
 *                             the canvas grows past 600 when content overflows)
 *   - color #fff             (the ref keeps body color:#fff — default ink
 *                             stays the near-white harness family on every
 *                             surface, deliberately camouflaged on the white
 *                             canvas; see capture-browser-ref.mjs's injection
 *                             note for why a black-ink flip is deferred)
 * `overflow:hidden` keeps each canvas's paint confined to its own box so one
 * test can't bleed into the next crop — the same isolation CaptureGallery's
 * canvasStyle uses. wave-25 round 3: the `transform:translateZ(0)` +
 * `position:relative` half of that isolation MOVED to composedIcbStyle
 * below, because those two declarations are what make a box the containing
 * block for abspos AND fixed descendants — and that containing block must be
 * the ref's 358-wide render viewport, not the 390-wide framed image. Fixed
 * descendants are still confined (the transform still exists, one level in),
 * so the anti-bleed guarantee is unchanged.
 */
const composedCanvasStyle: React.CSSProperties = {
  width: '390px',
  minHeight: '600px',
  boxSizing: 'border-box',
  // The four `padding-*` longhands are written by ComposedTestCanvas (all
  // four = CANVAS_FRAME_PX). Keeping a shorthand `padding` here as well
  // would mix shorthand+longhand in one React style object.
  background: CANVAS_BG_DEFAULT,
  // corpus-v4.1 BLACK ink (wave-12 catch): this canvas was the ONE surface
  // the v4.1 ink flip missed — inheriting prose (NodeRenderer's bare text
  // spans) computed white-on-white and vanished from every composed web
  // capture (css-flexbox web fell to 4/12 on invisible-prose tests while
  // the prose-free pair scored 1.0000). The ref injects color:#000
  // (capture-browser-ref.mjs REF header, v4.1 sub-boundary); this canvas
  // must match. Author-declared colors still win (inline styles beat the
  // canvas inheritance).
  color: '#000',
  overflow: 'hidden',
};

/**
 * The INITIAL CONTAINING BLOCK box inside the framed canvas — wave-25
 * round 3. This div, not the canvas, is the web analogue of the ref's render
 * VIEWPORT:
 *   - 358 x 568 minimum   (ICB_WIDTH_PX / ICB_MIN_HEIGHT_PX = the ref's
 *                          REF_RENDER_WIDTH / REF_RENDER_MIN_HEIGHT; it grows
 *                          past the floor with content, like the ref's
 *                          documentHeight capture)
 *   - position:relative   (makes it the containing block for abspos
 *     + transform          descendants, and the transform additionally
 *                          captures `position: fixed` ones — CSS Transforms
 *                          §3: a transformed element is the containing block
 *                          for fixed descendants. Both moved here from the
 *                          outer canvas so out-of-flow boxes anchor at the
 *                          CONTENT corner (16,16), which is where the
 *                          image-space-framed ref raster puts them.)
 *   - transparent         (the outer canvas paints the background, so the
 *                          frame band shows the page/body colour exactly as
 *                          the ref's sampled image pad does)
 *   - display:flow-root   (wave-40 lane T1 — see below)
 * The author body padding (if any) is written on top per side.
 *
 * ── wave-40 lane T1: THE MISSING BFC (the ref's `display: flow-root` twin) ──
 *
 * THE DEFECT. This div is the web twin of the box the ref is laid out in, and
 * capture-browser-ref.mjs's `canvasFrameCss()` pins
 * `:where(body) { display: flow-root; box-sizing: border-box; min-height: 100vh }`
 * on that box for one stated reason: "the body establishes a BFC so a first
 * child's block margin cannot escape through the body edge (it would shift the
 * whole page relative to a harness canvas that clips it)". The harness twin
 * carried `box-sizing` and the min-height but NOT `flow-root`, so the two
 * boxes were not twins at all: a first in-flow root with a UA block margin —
 * every WPT test that opens `<p>Test passes if …</p>`, i.e. most of the corpus
 * — collapsed its 1em margin THROUGH this div and pushed the div's border box
 * down by that margin.
 *
 * WHY IT ONLY SHOWED ON OUT-OF-FLOW CONTENT. In-flow content does not move
 * (the child sits at the displaced div's top edge either way), so prose-only
 * tests were unaffected and the defect stayed invisible for 15 waves. But this
 * div is ALSO the containing block for every abspos/fixed descendant, and the
 * ref's is the viewport — which never moves. So `top: 60px` landed 16 px lower
 * than the ref put it, on EVERY test combining a prose intro with out-of-flow
 * boxes, and the canvas grew 16 px taller than the ref's 600.
 *
 * MEASURED (puppeteer, this exact canvas + ICB markup — _diag40/T1/probe5.mjs;
 * scene = the css-transform-3d-rotateX-positive body):
 *                       canvas h   ICB y   <p> y   abspos box
 *   without flow-root      616       32      32      (76, 92)
 *   with    flow-root      600       16      32      (76, 76)
 *   browser-ref            600       16      32      (76, 76)
 * In-flow content is byte-identical (the `<p>` stays at y=32 in both); only
 * the out-of-flow anchor and the canvas height move, both ONTO the ref.
 * On the wave39-final css-transforms head this single 16 px displacement is
 * the whole of the eight-strong `css-transform-3d-rotate{3d-,}{X,Y}-*` cluster
 * (web 0.9293 against iOS 0.9988), plus css3-transform-scale-002 and
 * css-transform-3d-transform-style.
 *
 * `flow-root` — not `overflow:hidden`, not a 1px padding — because it is
 * EXACTLY what the ref declares: same BFC, same float containment, same
 * margin-escape block, and it adds no clip and no geometry of its own.
 */
const composedIcbStyle: React.CSSProperties = {
  width: '100%',
  minHeight: `${ICB_MIN_HEIGHT_PX}px`,
  boxSizing: 'border-box',
  // The ref's `:where(body){ display: flow-root }` twin — pins this box's
  // border-box top at the frame corner so out-of-flow descendants anchor
  // where the image-space-framed ref raster puts them (banner above).
  display: 'flow-root',
  position: 'relative',
  transform: 'translateZ(0)',
};

export default ComposedCaptureGallery;
