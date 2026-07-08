// BlendModeExtractor.ts — parses MixBlendMode (string) and BackgroundBlendMode
// (array of strings).  IR emits uppercase SNAKE_CASE (e.g. 'PLUS_LIGHTER'); we
// lowercase + hyphenate to the CSS token.

import type { BlendModeConfig, BlendModePropertyType } from './BlendModeConfig';
import {
  MIX_BLEND_MODE_PROPERTY,
  BACKGROUND_BLEND_MODE_PROPERTY,
} from './BlendModeConfig';

// Minimal IR property shape.
interface IRPropertyLike { type: string; data: unknown; }

// Registry predicate.
export function isBlendModeProperty(type: string): type is BlendModePropertyType {
  return type === MIX_BLEND_MODE_PROPERTY || type === BACKGROUND_BLEND_MODE_PROPERTY;
}

// Normalise one enum token to the CSS keyword form.
function toCssKeyword(raw: unknown): string | null {
  if (typeof raw !== 'string') return null;                           // ignore non-strings
  const lc = raw.toLowerCase().replace(/_/g, '-');                    // kebab-case per CSS spec
  if (lc.length === 0) return null;                                   // defensively reject empty
  return lc;                                                          // trust IR to only emit valid modes
}

// Entry point — produces both `mix` and `background` slots in one pass.
export function extractBlendMode(properties: IRPropertyLike[]): BlendModeConfig {
  const cfg: BlendModeConfig = {};                                    // blank accumulator
  for (const p of properties) {                                       // one pass over inputs
    if (p.type === MIX_BLEND_MODE_PROPERTY) {                         // single keyword
      const kw = toCssKeyword(p.data);                                // normalise
      if (kw) cfg.mix = kw;                                           // last write wins
    } else if (p.type === BACKGROUND_BLEND_MODE_PROPERTY) {           // array of keywords
      const arr = Array.isArray(p.data) ? p.data : [p.data];          // scalar -> singleton
      const mapped = arr                                              // convert each entry
        .map(toCssKeyword)                                            // -> string|null
        .filter((v): v is string => v !== null);                      // drop nulls
      if (mapped.length > 0) cfg.background = mapped;                 // only set when any survived
    }
  }
  return cfg;
}
