// FontFamilyExtractor.ts — folds `FontFamily` IR properties into a FontFamilyConfig.
// Family: font-family.  IR shapes catalogued during Phase-6 survey of
// examples/properties/typography/*.json after `./gradlew run` conversion.

import { FontFamilyConfig, FONT_FAMILY_PROPERTY_TYPE, FontFamilyPropertyType } from './FontFamilyConfig';

// Minimal IRProperty shape — keeps engine modules decoupled from IR types dir.
interface IRPropertyLike { type: string; data: unknown; }

// Narrowing predicate; mirrors the other engine modules (Phase 4/5 pattern).
export function isFontFamilyProperty(type: string): type is FontFamilyPropertyType {
  return type === FONT_FAMILY_PROPERTY_TYPE;                                           // exact string match
}

// Per-family parse routine — returns the CSS value string (or undefined to drop).
function parse(data: unknown): string | number | undefined {
  // FontFamily: IR emits ["Serif","sans-serif"] (string[]).  Quote any family
  // whose name contains whitespace so the browser treats it as a single token;
  // generic families (serif/sans-serif/monospace/...) are never quoted.
  const GENERIC = new Set(['serif','sans-serif','monospace','cursive','fantasy',
    'system-ui','ui-serif','ui-sans-serif','ui-monospace','ui-rounded','emoji','math','fangsong']);
  const list: string[] = [];                                         // output accumulator
  const raw = Array.isArray(data)                                    // accept array or bare object
    ? data
    : (data && typeof data === 'object' && Array.isArray((data as Record<string, unknown>).families))
      ? (data as Record<string, unknown>).families as unknown[]
      : (typeof data === 'string' ? [data] : []);
  for (const f of raw) {                                             // iterate families
    if (typeof f !== 'string' || f.length === 0) continue;           // skip garbage
    const lower = f.toLowerCase();                                   // case-insensitive generic check
    if (GENERIC.has(lower)) list.push(lower);                        // emit generic unquoted
    else if (/[\s"']/.test(f)) list.push(`"${f.replace(/"/g,'\\"')}"`); // quote custom w/ space
    else list.push(f);                                               // single-token custom unquoted
  }
  return list.length ? list.join(', ') : undefined;                  // CSS comma-separated list
}

// Main entrypoint — last write wins, mirroring CSS cascade semantics.
export function extractFontFamily(properties: IRPropertyLike[]): FontFamilyConfig {
  const cfg: FontFamilyConfig = {};                                      // blank accumulator
  for (const p of properties) {
    if (!isFontFamilyProperty(p.type)) continue;                         // filter unrelated
    const v = parse(p.data);                                          // convert payload
    if (v !== undefined) cfg.value = v;                               // record result
  }
  return cfg;
}
