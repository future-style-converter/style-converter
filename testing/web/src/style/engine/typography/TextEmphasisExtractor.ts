// TextEmphasisExtractor.ts — folds `TextEmphasis` IR properties into a TextEmphasisConfig.
// Family: text-emphasis.  IR shapes catalogued during Phase-6 survey of
// examples/properties/typography/*.json after `./gradlew run` conversion.

import { TextEmphasisConfig, TEXT_EMPHASIS_PROPERTY_TYPE, TextEmphasisPropertyType } from './TextEmphasisConfig';
import { kwLower, colorCss } from './_shared';

// Minimal IRProperty shape — keeps engine modules decoupled from IR types dir.
interface IRPropertyLike { type: string; data: unknown; }

// Narrowing predicate; mirrors the other engine modules (Phase 4/5 pattern).
export function isTextEmphasisProperty(type: string): type is TextEmphasisPropertyType {
  return type === TEXT_EMPHASIS_PROPERTY_TYPE;                                           // exact string match
}

// Per-family parse routine — returns the CSS value string (or undefined to drop).
function parse(data: unknown): string | number | undefined {
  // TextEmphasis (shorthand): {style:{...}, color:{...}}.
  // Emit as '<style> <color>' per the spec.
  if (!data || typeof data !== 'object') return undefined;
  const o = data as Record<string, unknown>;
  // Parse style sub-object via the same rules as TextEmphasisStyle.
  let styleStr: string | undefined;
  if (o.style && typeof o.style === 'object') {
    const s = o.style as Record<string, unknown>;
    if (s.type === 'custom' && typeof s.character === 'string') {
      styleStr = `"${s.character.replace(/"/g, '\\"')}"`;
    } else if (typeof s.type === 'string') {
      const kw = kwLower(s.type);
      styleStr = kw && (kw.startsWith('filled-') || kw.startsWith('open-'))
        ? kw.replace('-', ' ')
        : kw;
    }
  }
  const colorStr = o.color ? colorCss(o.color) : undefined;
  const parts: string[] = [];
  if (styleStr) parts.push(styleStr);
  if (colorStr) parts.push(colorStr);
  return parts.length ? parts.join(' ') : undefined;
}

// Main entrypoint — last write wins, mirroring CSS cascade semantics.
export function extractTextEmphasis(properties: IRPropertyLike[]): TextEmphasisConfig {
  const cfg: TextEmphasisConfig = {};                                      // blank accumulator
  for (const p of properties) {
    if (!isTextEmphasisProperty(p.type)) continue;                         // filter unrelated
    const v = parse(p.data);                                          // convert payload
    if (v !== undefined) cfg.value = v;                               // record result
  }
  return cfg;
}
