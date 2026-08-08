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

// wave-37 lane W2 — the authored <color-interpolation-method>.
// The converter now carries it on the gradient layer as the optional `interp`
// key (canonical CSS spelling, e.g. 'in oklch' / 'in hsl longer hue'); before
// that it was peeled off and thrown away, so every polar-space gradient in the
// corpus rendered an sRGB ramp.  Chrome has interpolated in every predefined
// and polar space since 111 (verified on the capture browser itself), so the
// web runtime's whole job here is to put the clause back into the syntax
// prefix, where css-images-4 §3.1's `||` combinator allows it beside the
// angle/shape.  A layer without the key emits byte-identical CSS to before.
// The value is a CLOSED grammar on the producing side (GradientValueParsers'
// INTERPOLATION_COLORSPACES × HUE_METHODS), but it arrives as wire text, so it
// is re-validated here rather than trusted into a declaration.
// css-color-4 §12.4 splits the spaces in two, and the split is LOAD-BEARING:
//   <color-interpolation-method> = in [ <rectangular-color-space>
//                                     | <polar-color-space> <hue-interpolation-method>? ]
// A hue method on a rectangular space does not parse. Measured on the capture
// browser: CSS.supports rejects `in oklab longer hue` for all 11 rectangular
// spaces × all 4 hue methods, and an unparsed gradient takes the WHOLE
// background-image declaration with it. The converter records the authored
// text verbatim (that is its job); this table is where it becomes CSS, so
// this is where the grammar is enforced.
const INTERP_RECTANGULAR = new Set([
  'srgb', 'srgb-linear', 'display-p3', 'a98-rgb', 'prophoto-rgb', 'rec2020',
  'lab', 'oklab', 'xyz', 'xyz-d50', 'xyz-d65',
]);
const INTERP_POLAR = new Set(['hsl', 'hwb', 'lch', 'oklch']);
const INTERP_HUE_METHODS = new Set(['shorter', 'longer', 'increasing', 'decreasing']);

// `interp` → a validated ' in <space>[ <hue-method> hue]' fragment, or ''.
export function interpClause(obj: Record<string, unknown>): string {
  const raw = obj.interp;                                              // absent on most layers
  if (typeof raw !== 'string') return '';                              // nothing authored → nothing emitted
  const t = raw.trim().toLowerCase().split(/\s+/);                     // 'in oklch longer hue'
  if (t.length !== 2 && t.length !== 4) return '';                     // only the two legal shapes
  if (t[0] !== 'in') return '';
  const polar = INTERP_POLAR.has(t[1]);
  if (!polar && !INTERP_RECTANGULAR.has(t[1])) return '';              // unknown space → drop, never emit
  if (t.length === 2) return ` in ${t[1]}`;
  // A hue tail is only grammatical on a polar space; anything else means the
  // author's own declaration was invalid, and re-emitting the invalid form
  // would delete a gradient we render today. Drop the clause, keep the ramp.
  if (!polar || !INTERP_HUE_METHODS.has(t[2]) || t[3] !== 'hue') return '';
  return ` in ${t[1]} ${t[2]} hue`;
}

// Reconstruct a linear-gradient() / repeating-linear-gradient() layer.
function linearGradientCss(obj: Record<string, unknown>, repeating: boolean): string {
  const angle = extractAngle(obj.angle);                               // optional — default 180deg per spec
  const deg = angle !== null ? angle.degrees : 180;                    // spec default (top-to-bottom)
  const stops = stopsToCss((obj.stops as IRStop[] | undefined) ?? []); // color-stop list
  const prefix = repeating ? 'repeating-' : '';                        // repeating variant prefix
  const interp = interpClause(obj);                                    // ' in oklch longer hue' or ''
  return `${prefix}linear-gradient(${deg}deg${interp}, ${stops})`;     // final CSS fragment
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
  const interp = interpClause(obj);                                    // ' in oklch shorter hue' or ''
  // The method may be the WHOLE prefix (`radial-gradient(in oklab, red, blue)`),
  // so it is appended before the head-emptiness test, not after.
  const head2 = (shape ? `${shape}${at}` : (at ? at.trimStart() : '')) + interp;
  const stops = stopsToCss(rest);                                      // real color stops only
  const prefix = repeating ? 'repeating-' : '';                        // repeating variant prefix
  return head2.trim()                                                  // 'circle at 50% 50%, red, blue'
    ? `${prefix}radial-gradient(${head2.trim()}, ${stops})`
    : `${prefix}radial-gradient(${stops})`;                            // no shape -> CSS uses default
}

// Reconstruct a conic-gradient layer.
function conicGradientCss(obj: Record<string, unknown>, repeating: boolean): string {
  const angle = extractAngle(obj.angle);                               // 'from X' starting angle
  const from = angle !== null ? `from ${angle.degrees}deg` : '';       // omit when not specified
  const at = atClause(obj);                                            // ' at …' via pos-key fix (A-RC5)
  const head = (from + at + interpClause(obj)).trim();                 // combined prefix
  const stops = stopsToCss((obj.stops as IRStop[] | undefined) ?? []); // stops
  const prefix = repeating ? 'repeating-' : '';                        // repeating variant prefix
  return head                                                          // 'from 30deg at 50% 50%, red, blue'
    ? `${prefix}conic-gradient(${head}, ${stops})`
    : `${prefix}conic-gradient(${stops})`;                             // stops-only form
}

// wave-37 lane W2 — the `{raw: …}` <image> passthrough.
//
// BackgroundImagePropertyParser drops any <image> its typed grammar cannot
// model into `{raw: <author bytes>}` — its documented "original author bytes
// for runtime resolution" fallback.  This module was returning null for every
// one of them, so the layer VANISHED: no background-image declaration at all.
// Measured on css-images: 28 image-set() tests (the converter has no
// image-set model — css-images-4 §2.4) and 12 gradients whose stops use a
// colour syntax ColorParser declines (`lch(50% 100% 0deg)`) all painted
// nothing, at ssim 0.9435–0.9705 with presence + coverage vetoes.
//
// The web runtime is exactly the place that resolution belongs: the value is
// CSS, and the browser is the CSS engine.  So the raw bytes are re-emitted —
// but only through this gate:
//   * a CLOSED head allow-list, so a raw value that is not an <image>
//     function (a var() reference, a stray keyword) is still dropped;
//   * paren balance + no ';' or '}', so a malformed corpus value can never
//     escape its inline-style declaration;
//   * SINGLE-LAYER ONLY (see parseLayers): in a comma-joined multi-layer
//     declaration one invalid layer would take the valid ones down with it,
//     which is the only way this could lose a currently-passing cell.
const PASSTHROUGH_IMAGE_FNS = [
  'image-set(', '-webkit-image-set(',                                  // css-images-4 §2.4
  'linear-gradient(', 'repeating-linear-gradient(',                    // css-images-3 §3.1
  'radial-gradient(', 'repeating-radial-gradient(',                    // css-images-3 §3.5
  'conic-gradient(', 'repeating-conic-gradient(',                      // css-images-4 §3.4.4
];

function rawImageFunctionCss(raw: string): string | null {
  const v = raw.trim();
  const head = v.toLowerCase();
  if (!PASSTHROUGH_IMAGE_FNS.some((fn) => head.startsWith(fn))) return null;
  // ';' or '}' inside an inline style value would let a malformed corpus
  // value escape its declaration — refuse rather than sanitise.
  if (v.includes(';') || v.includes('}')) return null;
  let depth = 0;                                                       // paren balance
  for (const ch of v) {
    if (ch === '(') depth++;
    else if (ch === ')') { depth--; if (depth < 0) return null; }       // closes too early
  }
  if (depth !== 0) return null;                                        // unbalanced → not a whole function
  if (!v.endsWith(')')) return null;                                   // trailing junk after the function
  return v;
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
    if (css !== null) { layers.push({ css }); continue; }              // keep successful reconstructions
    // Untyped `{raw:…}` <image> — hand the author's bytes to the browser,
    // but ONLY when it is the whole value (see rawImageFunctionCss).  This
    // lives here rather than in layerCss so the shared serialiser mask-image
    // and cross-fade recurse through stays byte-identical.
    if (arr.length === 1 && entry && typeof entry === 'object') {
      const raw = (entry as Record<string, unknown>).raw;
      if (typeof raw === 'string') {
        const passthrough = rawImageFunctionCss(raw);
        if (passthrough !== null) layers.push({ css: passthrough });
      }
    }
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
