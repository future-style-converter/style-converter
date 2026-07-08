// BackfaceVisibilityConfig.ts — CSS `backface-visibility` (Transforms L2).
// https://developer.mozilla.org/docs/Web/CSS/backface-visibility  IR: 'VISIBLE'|'HIDDEN'.
export interface BackfaceVisibilityConfig { value?: string; }
export const BACKFACE_VISIBILITY_PROPERTY_TYPE = 'BackfaceVisibility' as const;
export type BackfaceVisibilityPropertyType = typeof BACKFACE_VISIBILITY_PROPERTY_TYPE;
