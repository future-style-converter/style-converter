// _shared.ts — primitives used by the typography triplets.
// Centralises IR-shape tolerance (keywords, keyword lists, lengths, colors,
// numbers, angles) so each {Property}{Config,Extractor,Applier}.ts file stays
// tiny and stays well under the 200-line CLAUDE.md ceiling.
//
// Every helper is a pure function and totally tolerant of unknown shapes —
// returns `undefined` instead of throwing so cascade last-write-wins is safe.

import { extractLength, toCssLength, type LengthValue } from '../core/types/LengthValue'; // shared length alphabet
import { extractColor, type ColorValue } from '../core/types/ColorValue';                 // shared color alphabet
import { colorToCss } from '../color/DynamicColorCss';                                    // dynamic color reconstruction
import { extractKeyword } from '../core/types/KeywordValue';                              // kebab-case normaliser
import { extractAngle } from '../core/types/AngleValue';                                  // degree normaliser

// Re-export so per-property files import from one place.
export { toCssLength, colorToCss };
export type { LengthValue, ColorValue };

// Lowercase + kebab-case a bare IR enum string ('INTER_WORD' -> 'inter-word').
// Most typography enums emit this shape; the central helper keeps casing uniform.
export function kwLower(data: unknown): string | undefined {
  const kw = extractKeyword(data);                                // shared normaliser
  return kw ? kw.normalized : undefined;                          // already kebab-case
}

// Parse an IR value of shape `["A","B_C"]` (array of enum tokens) into a
// space-separated CSS value ("a b-c").  Used by font-variant-*/hanging-punctuation/
// text-decoration-line/text-underline-position/text-space-trim/text-autospace etc.
export function kwList(data: unknown): string | undefined {
  if (!Array.isArray(data) || data.length === 0) return undefined; // nothing to emit
  const tokens = data.map((t) => kwLower(t)).filter(Boolean) as string[]; // normalise each
  return tokens.length ? tokens.join(' ') : undefined;             // CSS space-separated list
}

// Parse a length-or-keyword shape: returns CSS string or undefined.
// Handles the common LengthValue alphabet + bare keyword fallback for cases
// where the parser emits `"normal"` or `"auto"` as a bare string.
export function lengthCss(data: unknown): string | undefined {
  // Bare string -> treat as keyword (e.g. 'normal', 'auto') — some typography
  // parsers emit bare strings before hitting the length rule.
  if (typeof data === 'string') return kwLower(data);
  const v = extractLength(data);                                   // try length alphabet
  if (v.kind === 'unknown') {                                      // fall back to keyword parse
    const kw = kwLower(data);
    return kw;
  }
  return toCssLength(v);                                           // '8px' / '0.5em' / 'auto' / ...
}

// Percentage-envelope helper: IR emits { type:'percentage', value:N }.
export function percentCss(data: unknown): string | undefined {
  if (data && typeof data === 'object') {                          // shape guard
    const o = data as Record<string, unknown>;
    if (o.type === 'percentage' && typeof o.value === 'number') return `${o.value}%`;
  }
  return undefined;                                                // caller tries next shape
}

// Extract a single finite number from common IR number shapes.
// Handles bare numbers, `{value:N}`, `{count:N}`, and typed `{type:'number', value:N}`.
export function numberOf(data: unknown): number | undefined {
  if (typeof data === 'number' && Number.isFinite(data)) return data;
  if (data && typeof data === 'object') {
    const o = data as Record<string, unknown>;
    if (typeof o.value === 'number' && Number.isFinite(o.value)) return o.value;
    if (typeof o.count === 'number' && Number.isFinite(o.count)) return o.count;
  }
  return undefined;
}

// Color -> CSS string via the shared extractColor + colorToCss round-trip.
// Identical to the Phase-4/5 path — dynamic colors (currentColor/color-mix/...)
// roundtrip unchanged, static sRGB becomes rgba(...).
export function colorCss(data: unknown): string | undefined {
  const c = extractColor(data);                                    // alpha handled inside
  if (c.kind === 'unknown') return undefined;                      // drop unparseable
  return colorToCss(c);                                            // static or dynamic passthrough
}

// Parse a `{type:'angle', deg:N, original?:{v,u}}` shape into a CSS `<angle>` string.
// Returns undefined on failure so callers can chain fallbacks.
export function angleCss(data: unknown): string | undefined {
  const a = extractAngle(data);                                    // normalise to degrees
  if (!a) return undefined;                                        // no recognised angle
  return `${a.degrees}deg`;                                        // CSS always accepts deg
}

// Predicate for the registry/renderer gate: typography properties are enumerated
// in PropertyRegistry.ts; this helper is unused at runtime but kept for parity
// with the spacing/borders _shared modules.
export function isOneOf(type: string, set: ReadonlySet<string>): boolean {
  return set.has(type);
}
