// TextEmphasisStyleExtractor.ts — folds `TextEmphasisStyle` IR properties into a TextEmphasisStyleConfig.
// Family: text-emphasis-style.  IR shapes catalogued during Phase-6 survey of
// examples/properties/typography/*.json after `./gradlew run` conversion.

import { TextEmphasisStyleConfig, TEXT_EMPHASIS_STYLE_PROPERTY_TYPE, TextEmphasisStylePropertyType } from './TextEmphasisStyleConfig';
import { kwLower } from './_shared';

// Minimal IRProperty shape — keeps engine modules decoupled from IR types dir.
interface IRPropertyLike { type: string; data: unknown; }

// Narrowing predicate; mirrors the other engine modules (Phase 4/5 pattern).
export function isTextEmphasisStyleProperty(type: string): type is TextEmphasisStylePropertyType {
  return type === TEXT_EMPHASIS_STYLE_PROPERTY_TYPE;                                           // exact string match
}

// Per-family parse routine — returns the CSS value string (or undefined to drop).
function parse(data: unknown): string | number | undefined {
  // TextEmphasisStyle flavours (see TextEmphasisStylePropertyParser.kt):
  //   {type:'none'|'filled'|'open'|'dot'|'circle'|'double-circle'|'triangle'|'sesame'}
  //   {type:'custom', character:'*'}  — single-grapheme custom mark
  //   Combined forms like 'filled circle' are emitted as {type:'...'} already.
  if (!data || typeof data !== 'object') return kwLower(data);
  const o = data as Record<string, unknown>;
  if (o.type === 'custom' && typeof o.character === 'string') {
    return `"${o.character.replace(/"/g, '\\"')}"`;                 // CSS double-quoted string
  }
  // Fill/shape combo: 'filled circle'/'open dot' arrive as 'filled-circle' via kebab normalisation;
  // replace the dash with a space so it matches the CSS grammar.
  const kw = kwLower(o.type);
  if (!kw) return undefined;
  return kw.includes('-') && (kw.startsWith('filled-') || kw.startsWith('open-'))
    ? kw.replace('-', ' ')
    : kw;
}

// Main entrypoint — last write wins, mirroring CSS cascade semantics.
export function extractTextEmphasisStyle(properties: IRPropertyLike[]): TextEmphasisStyleConfig {
  const cfg: TextEmphasisStyleConfig = {};                                      // blank accumulator
  for (const p of properties) {
    if (!isTextEmphasisStyleProperty(p.type)) continue;                         // filter unrelated
    const v = parse(p.data);                                          // convert payload
    if (v !== undefined) cfg.value = v;                               // record result
  }
  return cfg;
}
