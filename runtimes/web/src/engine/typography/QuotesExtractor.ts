// QuotesExtractor.ts — folds `Quotes` IR properties into a QuotesConfig.
// Family: quotes.  IR shapes catalogued during Phase-6 survey of
// examples/properties/typography/*.json after `./gradlew run` conversion.

import { QuotesConfig, QUOTES_PROPERTY_TYPE, QuotesPropertyType } from './QuotesConfig';
import { kwLower } from './_shared';

// Minimal IRProperty shape — keeps engine modules decoupled from IR types dir.
interface IRPropertyLike { type: string; data: unknown; }

// Narrowing predicate; mirrors the other engine modules (Phase 4/5 pattern).
export function isQuotesProperty(type: string): type is QuotesPropertyType {
  return type === QUOTES_PROPERTY_TYPE;                                           // exact string match
}

// Per-family parse routine — returns the CSS value string (or undefined to drop).
function parse(data: unknown): string | number | undefined {
  // Quotes: {type:'auto'|'none'} | {type:'pairs',pairs:[{open,close},...]}.
  if (!data || typeof data !== 'object') return kwLower(data);       // 'auto'|'none'
  const o = data as Record<string, unknown>;
  if (o.type === 'auto' || o.type === 'none') return String(o.type);
  if (o.type === 'pairs' && Array.isArray(o.pairs)) {                // quoted pair list
    const parts: string[] = [];
    for (const p of o.pairs) {                                       // iterate pairs
      if (!p || typeof p !== 'object') continue;
      const q = p as Record<string, unknown>;
      const open = typeof q.open === 'string' ? q.open : '';
      const close = typeof q.close === 'string' ? q.close : '';
      if (!open || !close) continue;
      parts.push(`"${open}" "${close}"`);                            // CSS requires double-quoted strings
    }
    return parts.length ? parts.join(' ') : undefined;
  }
  return undefined;
}

// Main entrypoint — last write wins, mirroring CSS cascade semantics.
export function extractQuotes(properties: IRPropertyLike[]): QuotesConfig {
  const cfg: QuotesConfig = {};                                      // blank accumulator
  for (const p of properties) {
    if (!isQuotesProperty(p.type)) continue;                         // filter unrelated
    const v = parse(p.data);                                          // convert payload
    if (v !== undefined) cfg.value = v;                               // record result
  }
  return cfg;
}
