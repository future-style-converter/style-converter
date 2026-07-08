// VisibilityConfig.ts — CSS `visibility`.
// https://developer.mozilla.org/docs/Web/CSS/visibility
// IR: 'VISIBLE' | 'HIDDEN' | 'COLLAPSE'.
export interface VisibilityConfig { value?: string; }
export const VISIBILITY_PROPERTY_TYPE = 'Visibility' as const;
export type VisibilityPropertyType = typeof VISIBILITY_PROPERTY_TYPE;
