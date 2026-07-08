// BackgroundImageExtractor.ts — builds a BackgroundImageConfig from one or more
// BackgroundImage IR properties.  Each IR payload is a list of layer entries;
// we reconstruct each to a CSS fragment string so the applier can comma-join.

import { extractColor } from '../core/types/ColorValue';
import { extractAngle } from '../core/types/AngleValue';
import { colorToCss } from '../color/DynamicColorCss';
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

// Reconstruct a radial-gradient layer.  Recovers shape/size tokens the parser
// mis-stuffed into the stops array.
function radialGradientCss(obj: Record<string, unknown>, repeating: boolean): string {
  const { head, rest } = splitStopsHead((obj.stops as IRStop[] | undefined) ?? []);
  const shape = head.join(' ');                                        // e.g. "circle closest-side"
  const pos = obj.position as Record<string, unknown> | undefined;     // optional position
  const at = pos ? ` at ${pos.x ?? 50}% ${pos.y ?? 50}%` : '';         // 'at X% Y%' suffix
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
  const pos = obj.position as Record<string, unknown> | undefined;     // optional position
  const at = pos ? ` at ${pos.x ?? 50}% ${pos.y ?? 50}%` : '';         // ' at X% Y%' suffix
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
    return `url(${entry})`;
  }
  if (typeof entry !== 'object') return null;                          // numbers etc. invalid here
  const obj = entry as Record<string, unknown>;
  if (obj.type === 'none') return 'none';                              // wrapped 'none'
  if (typeof obj.url === 'string') return `url(${obj.url})`;           // url layer (wrapped or unwrapped)
  if (obj.type === 'url' && typeof obj.url === 'string') return `url(${obj.url})`;
  switch (obj.type) {                                                  // gradient variants
    case 'linear-gradient':           return linearGradientCss(obj, false);
    case 'repeating-linear-gradient': return linearGradientCss(obj, true);
    case 'radial-gradient':           return radialGradientCss(obj, false);
    case 'repeating-radial-gradient': return radialGradientCss(obj, true);
    case 'conic-gradient':            return conicGradientCss(obj, false);
    case 'repeating-conic-gradient':  return conicGradientCss(obj, true);
    default:                           return null;                    // unknown -> drop
  }
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
