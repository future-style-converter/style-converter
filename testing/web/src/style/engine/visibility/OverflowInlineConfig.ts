// OverflowInlineConfig.ts — CSS `overflow-inline` (CSS Overflow L3, logical).
// https://developer.mozilla.org/docs/Web/CSS/overflow-inline
export interface OverflowInlineConfig { value?: string; }
export const OVERFLOW_INLINE_PROPERTY_TYPE = 'OverflowInline' as const;
export type OverflowInlinePropertyType = typeof OVERFLOW_INLINE_PROPERTY_TYPE;
