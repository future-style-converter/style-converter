// PerspectiveConfig.ts — CSS `perspective` (Transforms L2).
// https://developer.mozilla.org/docs/Web/CSS/perspective
// IR shapes (PerspectivePropertyParser.kt): {type:'none'} | {type:'length', px:N}.
export interface PerspectiveConfig { value?: string; }
export const PERSPECTIVE_PROPERTY_TYPE = 'Perspective' as const;
export type PerspectivePropertyType = typeof PERSPECTIVE_PROPERTY_TYPE;
