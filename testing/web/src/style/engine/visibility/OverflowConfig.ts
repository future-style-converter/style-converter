// OverflowConfig.ts — CSS `overflow` shorthand (one-value form).
// https://developer.mozilla.org/docs/Web/CSS/overflow
// IR: bare keyword ('VISIBLE' | 'HIDDEN' | 'CLIP' | 'SCROLL' | 'AUTO').
// The two-value form `overflow: x y` is expanded to OverflowX + OverflowY by
// the Kotlin parser, so here we only ever see a single keyword.
export interface OverflowConfig { value?: string; }
export const OVERFLOW_PROPERTY_TYPE = 'Overflow' as const;
export type OverflowPropertyType = typeof OVERFLOW_PROPERTY_TYPE;
