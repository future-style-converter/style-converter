// FontPaletteExtractor.ts — folds `FontPalette` IR properties into a FontPaletteConfig.
// Family: font-palette.  IR shapes catalogued during Phase-6 survey of
// examples/properties/typography/*.json after `./gradlew run` conversion.

import { FontPaletteConfig, FONT_PALETTE_PROPERTY_TYPE, FontPalettePropertyType } from './FontPaletteConfig';
import { kwLower } from './_shared';

// Minimal IRProperty shape — keeps engine modules decoupled from IR types dir.
interface IRPropertyLike { type: string; data: unknown; }

// Narrowing predicate; mirrors the other engine modules (Phase 4/5 pattern).
export function isFontPaletteProperty(type: string): type is FontPalettePropertyType {
  return type === FONT_PALETTE_PROPERTY_TYPE;                                           // exact string match
}

// Per-family parse routine — returns the CSS value string (or undefined to drop).
function parse(data: unknown): string | number | undefined {
  // FontPalette: {type:'normal'|'light'|'dark'} | {type:'custom',name:'--foo'}
  if (!data || typeof data !== 'object') return kwLower(data);
  const o = data as Record<string, unknown>;
  if (o.type === 'normal' || o.type === 'light' || o.type === 'dark') return String(o.type);
  if (o.type === 'custom' && typeof o.name === 'string') return o.name; // must begin with '--'
  return undefined;
}

// Main entrypoint — last write wins, mirroring CSS cascade semantics.
export function extractFontPalette(properties: IRPropertyLike[]): FontPaletteConfig {
  const cfg: FontPaletteConfig = {};                                      // blank accumulator
  for (const p of properties) {
    if (!isFontPaletteProperty(p.type)) continue;                         // filter unrelated
    const v = parse(p.data);                                          // convert payload
    if (v !== undefined) cfg.value = v;                               // record result
  }
  return cfg;
}
