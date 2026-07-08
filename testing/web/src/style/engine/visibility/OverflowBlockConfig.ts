// OverflowBlockConfig.ts — CSS `overflow-block` (CSS Overflow L3, logical).
// https://developer.mozilla.org/docs/Web/CSS/overflow-block
export interface OverflowBlockConfig { value?: string; }
export const OVERFLOW_BLOCK_PROPERTY_TYPE = 'OverflowBlock' as const;
export type OverflowBlockPropertyType = typeof OVERFLOW_BLOCK_PROPERTY_TYPE;
