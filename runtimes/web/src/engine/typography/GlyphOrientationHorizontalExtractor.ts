// GlyphOrientationHorizontalExtractor.ts — folds `GlyphOrientationHorizontal` IR properties into a GlyphOrientationHorizontalConfig.
// Family: glyph-orientation.  IR shapes catalogued during Phase-6 survey of
// examples/properties/typography/*.json after `./gradlew run` conversion.

import { GlyphOrientationHorizontalConfig, GLYPH_ORIENTATION_HORIZONTAL_PROPERTY_TYPE, GlyphOrientationHorizontalPropertyType } from './GlyphOrientationHorizontalConfig';
import { kwLower, angleCss } from './_shared';

// Minimal IRProperty shape — keeps engine modules decoupled from IR types dir.
interface IRPropertyLike { type: string; data: unknown; }

// Narrowing predicate; mirrors the other engine modules (Phase 4/5 pattern).
export function isGlyphOrientationHorizontalProperty(type: string): type is GlyphOrientationHorizontalPropertyType {
  return type === GLYPH_ORIENTATION_HORIZONTAL_PROPERTY_TYPE;                                           // exact string match
}

// Per-family parse routine — returns the CSS value string (or undefined to drop).
function parse(data: unknown): string | number | undefined {
  // GlyphOrientation: {deg:N,original?} | {type:'auto'} | {type:'angle', deg:N}.
  if (!data || typeof data !== 'object') return kwLower(data);
  const o = data as Record<string, unknown>;
  if (o.type === 'auto') return 'auto';
  if (typeof o.deg === 'number') return `${o.deg}deg`;
  return angleCss(data);                                             // shared angle parser
}

// Main entrypoint — last write wins, mirroring CSS cascade semantics.
export function extractGlyphOrientationHorizontal(properties: IRPropertyLike[]): GlyphOrientationHorizontalConfig {
  const cfg: GlyphOrientationHorizontalConfig = {};                                      // blank accumulator
  for (const p of properties) {
    if (!isGlyphOrientationHorizontalProperty(p.type)) continue;                         // filter unrelated
    const v = parse(p.data);                                          // convert payload
    if (v !== undefined) cfg.value = v;                               // record result
  }
  return cfg;
}
