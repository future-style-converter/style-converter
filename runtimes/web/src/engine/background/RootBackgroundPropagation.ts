// RootBackgroundPropagation.ts — wave-53 lane L3 (item A): the ROOT'S
// background-IMAGE layers propagate to the CANVAS (css-backgrounds-3 §2.11.2).
//
// WHY: the three composed canvases propagated the body-root's COLOUR layer
// only. A root `background-image` (css-display/display-contents-root-
// background's 1×1 green PNG; css-backgrounds/background-attachment-margin-
// root-001/-002's gradients) was painted by nobody across the canvas: the
// body-root is a SIBLING of the flow content in the flat v2 forest, so its own
// box (empty, or `display: contents`) covers nothing. MEASURED on wave52-ship:
// all 9 cells f 0.339–0.5355. This file is the PURE rule; the Compose and
// SwiftUI RootBackgroundPropagation twins implement the same decisions.
//
// The rule, per css-backgrounds-3:
//   §2.11.2 the root's background becomes the canvas background, its painting
//           area is the whole canvas, and the root does NOT paint it again
//           (→ withRootBackgroundStripped);
//   §3.4    a `fixed` layer is positioned relative to the VIEWPORT (the ICB),
//           a `scroll`/`local` one relative to the root's own box — here the
//           ICB plus the canvas-owned root margin (the merged html+body bag's
//           concrete margin, resolveCanvasMargin and its twins);
//   F2      the capture frame is CHROME: capture-browser-ref.mjs padColorFor
//           (:728) fills the 16-px image-space frame with the ring colour only
//           when the rendered ring is uniform, else with the white CANVAS_BG.
//           A stack that is uniform BY CONSTRUCTION (every layer one sRGBA and
//           repeating on both axes) may therefore paint the framed surface;
//           any other stack paints the ICB only and the frame keeps today's
//           colour-layer value. (Those predicates: RootBackgroundUniformity.ts.)
// The containment gate (css-contain-2 §2) is the caller's existing one —
// passed in as `contained`, so colour and image can never disagree on it.

import type { CSSProperties } from 'react';
// The runtime's own serialisers — the canvas paints byte-identical layer CSS
// to what the body-root's box would have emitted (no re-implemented parser).
import { extractBackgroundImage } from './BackgroundImageExtractor';
import { applyBackgroundImage } from './BackgroundImageApplier';
import { extractBackgroundSize } from './BackgroundSizeExtractor';
import { applyBackgroundSize } from './BackgroundSizeApplier';
import { extractBackgroundRepeat } from './BackgroundRepeatExtractor';
import { applyBackgroundRepeat } from './BackgroundRepeatApplier';
// No-silent-fallthrough breadcrumbs for the knobs this rule does not compose.
import { logUnhandled } from '../PropertyTracker';
// F2's "uniform BY CONSTRUCTION" predicates (split out at the wave-53 fix pass, PLAN §0 size rule).
import { isNoneLayer, stackIsUniform, type IRPropertyLike } from './RootBackgroundUniformity';
// Re-exported: the harness pins read the IHDR test from this module.
export { dataPngIs1x1 } from './RootBackgroundUniformity';

/** One image layer's §3.4 attachment keyword. */
export type RootLayerAttachment = 'scroll' | 'fixed' | 'local';

/** A layer's tile origin in CSS px, relative to the ICB (viewport) corner. */
export interface RootLayerOrigin { x: number; y: number }

/** The propagation decision for one document's body-root. */
export interface RootBackgroundPlan {
  attachments: RootLayerAttachment[];   // per image layer, source order (§2.3 cycling applied)
  origins: RootLayerOrigin[];           // per image layer, ICB-relative (§3.4)
  uniform: boolean;                     // F2: the stack paints one colour by construction
}

/** The image-layer longhands the canvas takes over (the root never repaints them). */
export const ROOT_BACKGROUND_IMAGE_TYPES: ReadonlySet<string> = new Set([
  'BackgroundImage', 'BackgroundSize', 'BackgroundRepeat', 'BackgroundAttachment',
  'BackgroundPosition', 'BackgroundPositionX', 'BackgroundPositionY',
  'BackgroundPositionInline', 'BackgroundPositionBlock',
  'BackgroundOrigin', 'BackgroundClip', 'BackgroundBlendMode',
]);

/** Knobs this rule does not compose with the origin offset (no corpus carrier). */
const UNMODELLED_TYPES = ['BackgroundPosition', 'BackgroundPositionX', 'BackgroundPositionY',
  'BackgroundPositionInline', 'BackgroundPositionBlock', 'BackgroundOrigin', 'BackgroundClip',
  'BackgroundBlendMode'];

/** The raw IR `BackgroundImage` layer list (last declaration wins, like the extractor). */
function imageLayers(props: IRPropertyLike[]): unknown[] {
  const d = [...props].reverse().find((p) => p.type === 'BackgroundImage')?.data;   // last wins (cascade)
  return Array.isArray(d) ? d : d === undefined || d === null ? [] : [d];   // scalar wrap
}

/** Layer i's attachment from the raw `BackgroundAttachment` list (§2.3: lists cycle). */
function attachmentAt(props: IRPropertyLike[], i: number): RootLayerAttachment {
  const d = [...props].reverse().find((p) => p.type === 'BackgroundAttachment')?.data;   // `[{type:'scroll'},…]`
  const list = Array.isArray(d) ? d : d === undefined ? [] : [d];         // scalar wrap
  if (list.length === 0) return 'scroll';                                 // initial value
  const e = list[i % list.length];                                        // cyclic pairing
  const kw = typeof e === 'string' ? e : (e as { type?: unknown } | null)?.type;   // bare or wrapped keyword
  const lc = typeof kw === 'string' ? kw.toLowerCase() : 'scroll';        // unknown → initial
  return lc === 'fixed' || lc === 'local' ? lc : 'scroll';                // `local` anchors like scroll (no scroller)
}

/**
 * The propagation plan for one body-root, or null when nothing propagates:
 * no image layer, only `none` layers, or containment (`contained`, the
 * caller's existing css-contain-2 §2 gate). `rootMargin` is the concrete
 * root margin the canvas owns (top/left in CSS px).
 */
export function rootBackgroundPlan(
  props: IRPropertyLike[], rootMargin: { top: number; left: number }, contained: boolean,
): RootBackgroundPlan | null {
  if (contained) return null;                                             // off the propagation path
  const layers = imageLayers(props);                                      // the raw per-layer list
  if (layers.length === 0 || layers.every(isNoneLayer)) return null;      // nothing to paint
  // Breadcrumb every knob the origin offset does not compose with (no carrier).
  for (const t of UNMODELLED_TYPES) {                                     // no corpus carrier pairs these
    if (props.some((p) => p.type === t)) logUnhandled(t, 'root canvas background: positioning area not modelled');
  }
  const attachments = layers.map((_, i) => attachmentAt(props, i));       // §3.4 per layer
  // §3.4: fixed → the viewport corner; scroll/local → the root box's padding edge.
  const origins = attachments.map((a) => (a === 'fixed' ? { x: 0, y: 0 } : { x: rootMargin.left, y: rootMargin.top }));
  // F2: every layer one colour AND repeating on both axes (no gaps) ⇒ the ring is uniform.
  return { attachments, origins, uniform: stackIsUniform(layers, props) };
}

/**
 * The canvas-surface CSS for a plan. `base` is where the ICB corner sits
 * inside the painted surface — 0 when painting the ICB div itself, the 16-px
 * frame when painting the framed outer div. Attachment is always `scroll`: a verbatim
 * `fixed` would anchor to the GALLERY page's viewport, not the test's (§3.4
 * is realised through the per-layer origin instead).
 */
export function rootCanvasBackgroundStyle(
  props: IRPropertyLike[], plan: RootBackgroundPlan, base: number,
): CSSProperties {
  const n = plan.origins.length;                                          // one entry per layer
  return {
    ...applyBackgroundImage(extractBackgroundImage(props as never)),      // the layers' own CSS
    ...applyBackgroundSize(extractBackgroundSize(props as never)),        // §3.9 sizes, verbatim
    ...applyBackgroundRepeat(extractBackgroundRepeat(props as never)),    // §3.7 repeats, verbatim
    backgroundPosition: plan.origins.map((o) => `${o.x + base}px ${o.y + base}px`).join(', '),
    backgroundOrigin: Array(n).fill('padding-box').join(', '),            // the surface's own corner
    backgroundClip: Array(n).fill('border-box').join(', '),               // §2.11.2: the whole surface
    backgroundAttachment: Array(n).fill('scroll').join(', '),             // never the gallery viewport
  };
}

/** §2.11.2 "not painted again": the body-root's properties minus the image layers. */
export function withRootBackgroundStripped<P extends IRPropertyLike>(props: P[]): P[] {
  return props.filter((p) => !ROOT_BACKGROUND_IMAGE_TYPES.has(p.type));
}
