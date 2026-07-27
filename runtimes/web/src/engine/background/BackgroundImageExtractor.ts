// BackgroundImageExtractor.ts — builds a BackgroundImageConfig from one or more
// BackgroundImage IR properties.  Each IR payload is a list of layer entries;
// we reconstruct each to a CSS fragment string so the applier can comma-join.

import { extractColor } from '../core/types/ColorValue';
import { extractAngle } from '../core/types/AngleValue';
import { colorToCss } from '../color/DynamicColorCss';
// Shared always-quoted url() serialiser (css-values-4 §4.5): an unquoted
// url() token cannot contain whitespace/quotes/parens, so data URIs with
// spaces or quotes emitted as `url(${raw})` produce an INVALID declaration
// the browser drops wholesale — the exact bug the border-image fix closed.
// Imported (not duplicated) so the escaping rules have one owner.
import { cssUrl } from '../borders/image/_shared';
// Shared cross-fade weight normalization (css-images-4 §2.6.2) — the
// byte-parallel twin of the Compose/SwiftUI CrossFadeMath. Web emits the
// EFFECTIVE weights explicitly so the browser's own defaulting can never
// diverge from what the native engines composite.
import { normalizeCrossFadeWeights } from './CrossFadeMath';
import type {
  BackgroundImageConfig,
  BackgroundImagePropertyType,
  BackgroundLayer,
} from './BackgroundImageConfig';
import { BACKGROUND_IMAGE_PROPERTY_TYPE } from './BackgroundImageConfig';

// Minimal IR property shape for decoupling.
interface IRPropertyLike { type: string; data: unknown; }

// Keyword strings the IR may emit inside a 'stops' list for radial-gradient
// shape/size (e.g. 'circle', 'ellipse', 'closest-side', 'farthest-corner').
// We pull these out and put them back at the front of the gradient declaration.
const RADIAL_SHAPE_WORDS = new Set([
  'circle', 'ellipse',                                                 // shape keywords
  'closest-side', 'closest-corner', 'farthest-side', 'farthest-corner',// size keywords
]);

// Registry predicate.
export function isBackgroundImageProperty(type: string): type is BackgroundImagePropertyType {
  return type === BACKGROUND_IMAGE_PROPERTY_TYPE;
}

// One entry in the IR 'stops' array — may be a real stop or a mis-parsed shape word.
interface IRStop { color?: unknown; position?: unknown }

// Walk stops, splitting out leading shape/size keywords from real color stops.
function splitStopsHead(stops: IRStop[]): { head: string[]; rest: IRStop[] } {
  const head: string[] = [];                                           // collected shape/size tokens
  let i = 0;                                                           // index of first real color stop
  while (i < stops.length) {
    const s = stops[i];
    const c = s.color as Record<string, unknown> | undefined;
    const orig = c && typeof c === 'object' ? c.original : undefined;
    if (typeof orig === 'string' && RADIAL_SHAPE_WORDS.has(orig)) {    // shape word masquerading as color
      head.push(orig);                                                 // preserve token
      i++;                                                             // advance past it
      continue;
    }
    break;                                                             // first real stop reached
  }
  return { head, rest: stops.slice(i) };                               // split into prefix + real stops
}

// Serialise real color stops: "color", "color N%", or skip unparseable entries.
function stopsToCss(stops: IRStop[]): string {
  const parts: string[] = [];                                          // CSS fragments
  for (const s of stops) {
    const color = extractColor(s.color);                               // parse IR color primitive
    if (color.kind === 'unknown') continue;                            // drop malformed
    const css = colorToCss(color);                                     // rgba(...) or dynamic
    const pos = s.position;                                            // may be null / number
    if (typeof pos === 'number') parts.push(`${css} ${pos}%`);         // explicit stop position
    else parts.push(css);                                              // no position -> CSS auto-spaces
  }
  return parts.join(', ') || 'transparent, transparent';               // fallback keeps CSS valid
}

// Reconstruct a linear-gradient() / repeating-linear-gradient() layer.
function linearGradientCss(obj: Record<string, unknown>, repeating: boolean): string {
  const angle = extractAngle(obj.angle);                               // optional — default 180deg per spec
  const deg = angle !== null ? angle.degrees : 180;                    // spec default (top-to-bottom)
  const stops = stopsToCss((obj.stops as IRStop[] | undefined) ?? []); // color-stop list
  const prefix = repeating ? 'repeating-' : '';                        // repeating variant prefix
  return `${prefix}linear-gradient(${deg}deg, ${stops})`;              // final CSS fragment
}

// One gradient-center axis → CSS text. The IR position axis is a
// <length-percentage> (IRLengthPercentageSerializer, ValueTypes.kt):
//   raw number            → percentage (legacy {"x":25} form)      → '25%'
//   {px: N}               → absolute length normalized to px       → '100px'
//   {original:{v,u}}      → runtime-dependent unit (lh/em/rem/…)   → '1lh'
// The web runtime serializes lengths VERBATIM — the browser resolves
// runtime-dependent units against the live font metrics (that is where
// they live on this platform), so nothing is dropped or approximated.
function positionAxisCss(axis: unknown): string {
  if (typeof axis === 'number') return `${axis}%`;                     // percent (raw-number wire)
  if (axis && typeof axis === 'object') {
    const o = axis as Record<string, unknown>;
    // Runtime-dependent unit — re-emit the original value+unit (the enum
    // name is uppercase on the wire, CSS wants lowercase; PERCENT is the
    // one enum whose CSS spelling is a symbol).
    const orig = o.original as Record<string, unknown> | undefined;
    if (orig && typeof orig.v === 'number' && typeof orig.u === 'string') {
      const unit = orig.u === 'PERCENT' ? '%' : orig.u.toLowerCase();
      return `${orig.v}${unit}`;
    }
    if (typeof o.px === 'number') return `${o.px}px`;                  // absolute length
  }
  return '50%';                                                        // defensive default = center
}

// Optional `at <position>` suffix shared by radial + conic. Reads the
// serializer's ACTUAL key "pos" first — the pre-wave-21 code read only
// the legacy "position" key, which the converter never emits, so EVERY
// web gradient rendered centered regardless of its `at` clause (A-RC5,
// corpus-wide; pinned by the wave21-gate conic-gradient-center IR
// artifact whose wire is {"pos":{"x":25,"y":25}}).
function atClause(obj: Record<string, unknown>): string {
  const pos = (obj.pos ?? obj.position) as Record<string, unknown> | undefined;
  return pos ? ` at ${positionAxisCss(pos.x)} ${positionAxisCss(pos.y)}` : '';
}

// Reconstruct a radial-gradient layer.  Recovers shape/size tokens the parser
// mis-stuffed into the stops array.
function radialGradientCss(obj: Record<string, unknown>, repeating: boolean): string {
  const { head, rest } = splitStopsHead((obj.stops as IRStop[] | undefined) ?? []);
  // Shape/size can ALSO arrive as first-class keys (post-Phase-12 wire).
  const shapeKey = typeof obj.shape === 'string' ? obj.shape : '';     // 'circle' | 'ellipse'
  const sizeKey = typeof obj.size === 'string' ? obj.size : '';        // 'closest-side' etc.
  const shape = [shapeKey, sizeKey, ...head].filter(Boolean).join(' ');// combined keyword prefix
  const at = atClause(obj);                                            // 'at …' via pos-key fix
  const head2 = shape ? `${shape}${at}` : (at ? at.trimStart() : '');  // combine shape + position
  const stops = stopsToCss(rest);                                      // real color stops only
  const prefix = repeating ? 'repeating-' : '';                        // repeating variant prefix
  return head2                                                         // 'circle at 50% 50%, red, blue'
    ? `${prefix}radial-gradient(${head2}, ${stops})`
    : `${prefix}radial-gradient(${stops})`;                            // no shape -> CSS uses default
}

// Reconstruct a conic-gradient layer.
function conicGradientCss(obj: Record<string, unknown>, repeating: boolean): string {
  const angle = extractAngle(obj.angle);                               // 'from X' starting angle
  const from = angle !== null ? `from ${angle.degrees}deg` : '';       // omit when not specified
  const at = atClause(obj);                                            // ' at …' via pos-key fix (A-RC5)
  const head = (from + at).trim();                                     // combined prefix
  const stops = stopsToCss((obj.stops as IRStop[] | undefined) ?? []); // stops
  const prefix = repeating ? 'repeating-' : '';                        // repeating variant prefix
  return head                                                          // 'from 30deg at 50% 50%, red, blue'
    ? `${prefix}conic-gradient(${head}, ${stops})`
    : `${prefix}conic-gradient(${stops})`;                             // stops-only form
}

// Reconstruct one layer from an arbitrary IR entry.  Returns null on garbage.
// Exported so sibling modules (engine/effects/mask/MaskImage*) can reuse the
// same layer serialiser — mask-image uses the identical grammar.
export function layerCss(entry: unknown): string | null {
  if (entry === null || entry === undefined) return null;              // null entries dropped
  if (typeof entry === 'string') {                                     // bare 'none' OR bare URL
    if (entry === 'none') return 'none';
    // Bug 5 — IRUrlSerializer (src/main/kotlin/app/irmodels/ValueTypes.kt
    // §IRUrlSerializer) encodes non-data URLs as bare JSON string
    // primitives. Previously we silently dropped them, breaking every
    // image-orientation / background-position WPT test that referenced
    // `support/*.jpg` files. Wrap the string as a `url(...)` layer so
    // the browser actually attempts the resource fetch. Data URLs are
    // unaffected — they round-trip through IRUrlSerializer as object
    // form { url, data:true } and reach the `obj.url` branch below.
    // See testing/titan/investigations/swarm-002/css-images__image-orientation-background-position.json.
    // Quoted via cssUrl — a bare `url(${entry})` is invalid CSS the
    // moment the path contains a space/quote/paren (same latent hole
    // the border-image fix closed; the whole declaration was dropped).
    return cssUrl(entry);
  }
  if (typeof entry !== 'object') return null;                          // numbers etc. invalid here
  const obj = entry as Record<string, unknown>;
  if (obj.type === 'none') return 'none';                              // wrapped 'none'
  if (typeof obj.url === 'string') return cssUrl(obj.url);             // url layer (wrapped or unwrapped), quoted
  if (obj.type === 'url' && typeof obj.url === 'string') return cssUrl(obj.url);
  switch (obj.type) {                                                  // gradient variants
    case 'linear-gradient':           return linearGradientCss(obj, false);
    case 'repeating-linear-gradient': return linearGradientCss(obj, true);
    case 'radial-gradient':           return radialGradientCss(obj, false);
    case 'repeating-radial-gradient': return radialGradientCss(obj, true);
    case 'conic-gradient':            return conicGradientCss(obj, false);
    case 'repeating-conic-gradient':  return conicGradientCss(obj, true);
    case 'cross-fade':                return crossFadeCss(obj);        // css-images-4 §2.6.2 (A-RC2)
    case 'color':                     return colorLayerCss(obj);       // <color> as image (cross-fade arg)
    default:                           return null;                    // unknown -> drop
  }
}

// A bare <color> used as an image. Valid DIRECTLY only inside
// cross-fade() (css-images-4 §2.6.2); when one appears as a standalone
// layer (e.g. via the mask reuse path) serialize the universally-valid
// equivalent image — a two-stop single-color gradient.
// The color payload of a {type:"color"} layer. TWO live wire shapes:
// the nested {"type":"color","color":{srgb,original}} the serializer
// builds, AND the flattened {"type":"color","srgb":…,"original":…} that
// IRPropertySerializer.deepFlatten produces on the real wire (it inlines
// any "type + single object field" pattern — pinned by the converter run
// on the premultiplied-alpha fixture). extractColor reads `srgb` either
// way, so passing the layer object itself covers the flattened form.
function colorLayerPayload(obj: Record<string, unknown>): unknown {
  return obj.color ?? obj;                                             // nested ?? flattened
}

function colorLayerCss(obj: Record<string, unknown>): string | null {
  const color = extractColor(colorLayerPayload(obj));                  // parse IR color primitive
  if (color.kind === 'unknown') return null;                           // drop malformed loudly upstream
  const css = colorToCss(color);                                       // rgba(...) or dynamic
  return `linear-gradient(${css}, ${css})`;                            // solid-color image equivalent
}

// One cross-fade argument image → CSS. wave-22: colors are gradient-
// wrapped (colorLayerCss) rather than emitted raw — the serialized output
// is now the LEGACY -webkit-cross-fade() chain (see crossFadeCss), whose
// arguments must be <image>s (bare colors are only legal in the modern
// grammar no shipping engine parses unflagged).
function crossFadeArgImageCss(image: unknown): string | null {
  if (image && typeof image === 'object'
      && (image as Record<string, unknown>).type === 'color') {
    return colorLayerCss(image as Record<string, unknown>);            // <color> → solid gradient image
  }
  return layerCss(image);                                              // any other <image> recurses
}

// A fully transparent <image> — the chain terminator that realises a
// sub-100% cross-fade sum (css-images-4 §2.6.2: the remaining share of the
// canvas stays TRANSPARENT). A two-stop transparent gradient is the
// universally-parsed spelling.
const TRANSPARENT_IMAGE_CSS = 'linear-gradient(transparent, transparent)';

// Percent formatter for the chain: 4-decimal rounding, trailing zeros
// dropped (0.1/0.3 folds would otherwise print float dust like
// 33.33333333333332%).
function chainPct(fraction: number): string {
  return `${+(fraction * 100).toFixed(4)}%`;
}

// Reconstruct a cross-fade() layer (css-images-4 §2.6.2). The IR carries
// AUTHORED weights (absent key = omitted); we normalize via the shared
// CrossFadeMath twin and emit EFFECTIVE percentages explicitly so the
// browser's compositing matches the Compose/SwiftUI weighted-sum math
// exactly. `legacy: true` (older two-arg trailing-percent syntax) emits
// `-webkit-cross-fade()` — the only form engines without the modern
// syntax implement — with the second image's normalized weight.
function crossFadeCss(obj: Record<string, unknown>): string | null {
  const args = obj.args as { weight?: unknown; image?: unknown }[] | undefined;
  if (!Array.isArray(args) || args.length === 0) return null;          // invalid wire -> drop
  // Authored weights: number | null (absent key = omitted percentage).
  const weights = args.map((a) => (typeof a.weight === 'number' ? a.weight : null));
  const fractions = normalizeCrossFadeWeights(weights);                // shared §2.6.2 math
  // Serialize each argument image; any failure drops the WHOLE function
  // (a partial cross-fade would re-weight the remaining images).
  const images = args.map((a) => crossFadeArgImageCss(a.image));
  if (images.some((i) => i === null)) return null;
  if (obj.legacy === true && images.length === 2) {
    // Legacy: percentage names the SECOND image's share.
    return `-webkit-cross-fade(${images[0]}, ${images[1]}, ${fractions[1] * 100}%)`;
  }
  // wave-22: the modern n-ary form used to be emitted here VERBATIM — but
  // no shipping engine parses unprefixed cross-fade() unflagged (Chromium
  // gates it behind CSSCrossFadeFunction), so the whole declaration was
  // INVALID in the capture browser and the layer silently dropped.
  // Measured on wave21-final css-images: cross-fade-target-alpha's inner
  // six-layer gradient rendered NOTHING (web 0.901 vs natives 0.98+), and
  // cross-fade-premultiplied-alpha's box was blank too (masked by fuzzy —
  // 0.9565). Lower the n-ary function onto an EQUIVALENT nested legacy
  // -webkit-cross-fade() chain instead — a left fold where image k+1
  // enters at its share of the running sum:
  //     C₁ = I₁,  Sₖ = f₁+…+fₖ,
  //     Cₖ₊₁ = -webkit-cross-fade(Cₖ, Iₖ₊₁, fₖ₊₁/Sₖ₊₁)
  // so by induction Cₖ carries each image at weight fᵢ/Sₖ. A sub-100%
  // total (§2.6.2 keeps the remainder TRANSPARENT — the target-alpha case,
  // six 10% layers = 60%) closes the chain with a transparent image at
  // weight 1−Sₙ. -webkit-cross-fade composites premultiplied, matching
  // the Compose/SwiftUI weighted-sum twins (CrossFadeMath pin table).
  // Zero-weight images contribute nothing and would fold as 0/0 — skip.
  const active = images
    .map((img, i) => ({ img: img as string, f: fractions[i] }))
    .filter((a) => a.f > 0);
  // All weights zero (e.g. authored 0%s): the §2.6.2 result is a fully
  // transparent image, not an invalid declaration.
  if (active.length === 0) return TRANSPARENT_IMAGE_CSS;
  let chain = active[0].img;                                           // C₁ = I₁
  let sum = active[0].f;                                               // S₁ = f₁
  for (let k = 1; k < active.length; k++) {
    const next = sum + active[k].f;                                    // Sₖ₊₁
    chain = `-webkit-cross-fade(${chain}, ${active[k].img}, ${chainPct(active[k].f / next)})`;
    sum = next;
  }
  // Remainder-transparent tail for sub-100% totals (float-tolerant guard:
  // a sum within 0.01% of 1 is "fully covered", no tail).
  if (sum < 0.9999) {
    chain = `-webkit-cross-fade(${chain}, ${TRANSPARENT_IMAGE_CSS}, ${chainPct(1 - sum)})`;
  }
  return chain;
}

// Parse one IR payload (either an array of layers or a single layer).
function parseLayers(data: unknown): BackgroundLayer[] {
  const arr = Array.isArray(data) ? data : [data];                     // single-layer shorthand
  const layers: BackgroundLayer[] = [];                                // accumulator
  for (const entry of arr) {
    const css = layerCss(entry);                                       // reconstruct one layer
    if (css !== null) layers.push({ css });                            // keep successful reconstructions
  }
  return layers;
}

// Entry point — last BackgroundImage property wins (CSS cascade semantics).
export function extractBackgroundImage(properties: IRPropertyLike[]): BackgroundImageConfig {
  const cfg: BackgroundImageConfig = { layers: [] };                   // empty accumulator
  for (const p of properties) {
    if (!isBackgroundImageProperty(p.type)) continue;                  // filter
    cfg.layers = parseLayers(p.data);                                  // last IR property replaces
  }
  return cfg;
}
