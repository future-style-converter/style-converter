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
//           colour-layer value.
// The containment gate (css-contain-2 §2) is the caller's existing one —
// passed in as `contained`, so colour and image can never disagree on it.

import type { CSSProperties } from 'react';
// The engine's own serialisers — the canvas paints byte-identical layer CSS
// to what the body-root's box would have emitted (no re-implemented parser).
import { extractBackgroundImage } from './BackgroundImageExtractor';
import { applyBackgroundImage } from './BackgroundImageApplier';
import { extractBackgroundSize } from './BackgroundSizeExtractor';
import { applyBackgroundSize } from './BackgroundSizeApplier';
import { extractBackgroundRepeat } from './BackgroundRepeatExtractor';
import { applyBackgroundRepeat } from './BackgroundRepeatApplier';
// No-silent-fallthrough breadcrumbs for the knobs this rule does not compose.
import { logUnhandled } from '../PropertyTracker';

/** Minimal IR property shape (decoupled from IRModels, like the extractors). */
interface IRPropertyLike { type: string; data?: unknown }

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

/** True for a `none` layer (bare string or `{type:'none'}`) — it paints nothing. */
function isNoneLayer(l: unknown): boolean {
  return l === 'none' || (typeof l === 'object' && l !== null && (l as { type?: unknown }).type === 'none');  // both shapes
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

/** One stop colour as a comparable sRGBA key, or null when it is not static sRGB. */
function stopKey(stop: unknown): string | null | undefined {
  const c = (stop as { color?: unknown } | null)?.color;                  // the stop's colour leaf
  if (c === undefined || c === null) return undefined;                    // a hint / shape word: no colour
  const s = (c as { srgb?: Record<string, unknown> }).srgb;               // the reader's normalized sRGB
  if (!s || typeof s.r !== 'number') return null;                         // currentColor / var(): unknown
  return `${s.r},${s.g},${s.b},${typeof s.a === 'number' ? s.a : 1}`;     // alpha defaults to opaque
}

/** css-images-3 §3: a gradient whose every stop shares one sRGBA paints that colour everywhere. */
function gradientIsUniform(l: { stops?: unknown }): boolean {
  if (!Array.isArray(l.stops)) return false;                              // malformed → not uniform
  const keys = l.stops.map(stopKey).filter((k) => k !== undefined);       // drop colour-less entries
  return keys.length > 0 && keys.every((k) => k !== null && k === keys[0]);   // one static sRGBA
}

/** Decode a `data:` URI's payload bytes (base64 or percent-encoded), or null. */
function dataUriBytes(url: string): Uint8Array | null {
  const comma = url.indexOf(',');                                         // RFC 2397: header,payload
  if (!url.startsWith('data:') || comma < 0) return null;                 // not a data URI
  const header = url.slice(5, comma); const body = url.slice(comma + 1);  // mediatype[;base64] , payload
  try {                                                                   // atob throws on bad base64
    if (/;base64$/i.test(header)) return Uint8Array.from(atob(body), (ch) => ch.charCodeAt(0));
    // Percent-encoding: decode %XX per byte (a binary PNG is not valid UTF-8).
    const out: number[] = [];                                             // the raw bytes
    for (let i = 0; i < body.length; i++) {                               // %XX → one byte, else verbatim
      if (body[i] === '%' && i + 2 < body.length) { out.push(parseInt(body.slice(i + 1, i + 3), 16)); i += 2; }
      else out.push(body.charCodeAt(i) & 0xff);                         // a literal byte
    }
    return Uint8Array.from(out);                                          // the raw PNG bytes
  } catch { return null; }                                                // bad base64 → unknown
}

/** PNG (ISO 15948 §5.2/§11.2.2): signature, then IHDR width/height — true iff 1×1. */
export function dataPngIs1x1(url: string): boolean {
  if (!/^data:image\/png[;,]/i.test(url)) return false;                   // PNG data URIs only
  const b = dataUriBytes(url);                                            // RFC 2397 payload
  if (!b || b.length < 24) return false;                                  // no full IHDR
  const sig = [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a];          // the PNG signature
  if (!sig.every((v, i) => b[i] === v)) return false;                     // not a PNG
  if (String.fromCharCode(b[12], b[13], b[14], b[15]) !== 'IHDR') return false;   // first chunk = IHDR
  const be32 = (o: number) => ((b[o] << 24) | (b[o + 1] << 16) | (b[o + 2] << 8) | b[o + 3]) >>> 0;  // big-endian
  return be32(16) === 1 && be32(20) === 1;                                // width, height
}

/** One layer paints a single colour across its tile (F2's per-layer test). */
function layerIsUniform(l: unknown): boolean {
  if (isNoneLayer(l)) return true;                                        // transparent everywhere
  if (typeof l !== 'object' || l === null) return false;                  // bare URL string: unknown pixels
  const o = l as { type?: unknown; url?: unknown; stops?: unknown };                    // url layer or gradient layer
  if (typeof o.url === 'string') return dataPngIs1x1(o.url);              // a 1×1 raster tile
  return typeof o.type === 'string' && /gradient$/.test(o.type) && gradientIsUniform(o);   // plain or repeating
}

/** §3.7: one raw repeat entry is `repeat` on both axes (string tokens or an {x,y} pair). */
function entryRepeatsBoth(e: unknown): boolean {
  const isRepeat = (v: unknown) => typeof v === 'string' && v.toLowerCase() === 'repeat';   // one axis keyword
  if (typeof e === 'string') return e.trim().split(/\s+/).every(isRepeat);  // 'repeat' / 'repeat repeat'
  const o = e as { x?: unknown; y?: unknown } | null;                     // any other keyword: a gap or one tile
  return !!o && typeof o === 'object' && isRepeat(o.x) && isRepeat(o.y);  // axis-pair shape
}

/** §3.7: every layer repeats on BOTH axes (absent leaf = initial `repeat`). */
function repeatsBothAxes(props: IRPropertyLike[]): boolean {
  const d = [...props].reverse().find((p) => p.type === 'BackgroundRepeat')?.data;   // e.g. ['no-repeat']
  const list = Array.isArray(d) ? d : d === undefined ? [] : [d];         // scalar wrap
  return list.every(entryRepeatsBoth);                                    // [] → initial repeat
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
  return { attachments, origins, uniform: layers.every(layerIsUniform) && repeatsBothAxes(props) };
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
