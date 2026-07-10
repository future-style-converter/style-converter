// ColorSchemeExtractor.ts — folds IR `ColorScheme` properties into a config.
// Wire shape (ColorSchemePropertyParser.kt → single-field data class,
// flattened): data: ['NORMAL'] | ['LIGHT','DARK'] | ['DARK','ONLY'] … — a
// bare array of SHOUTY enum tokens in declaration order.
import { foldLast, kebab, type IRPropertyLike } from '../_phase10_shared';
import { COLOR_SCHEME_PROPERTY_TYPE, type ColorSchemeConfig } from './ColorSchemeConfig';

// Join the scheme tokens back into the CSS space-separated list
// ('light dark', 'dark only', 'normal'). Order is preserved — it's
// meaningful per css-color-adjust-1 §2 (preference order).
function parse(data: unknown): string | undefined {
  if (!Array.isArray(data) || data.length === 0) return undefined;  // wire is always a non-empty array
  const parts = data.map(kebab);                                    // 'LIGHT' → 'light', 'ONLY' → 'only'
  if (parts.some((p) => p === undefined)) return undefined;         // any non-string entry → drop whole value
  return parts.join(' ');                                           // CSS space-separated list
}

// Single-pass fold, last write wins — matches every other engine extractor.
export function extractColorScheme(properties: IRPropertyLike[]): ColorSchemeConfig {
  return { value: foldLast(properties, COLOR_SCHEME_PROPERTY_TYPE, parse) };
}
