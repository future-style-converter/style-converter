// PerspectiveOriginConfig.ts — CSS `perspective-origin` (Transforms L2).
// https://developer.mozilla.org/docs/Web/CSS/perspective-origin
// IR shape (PerspectiveOriginPropertyParser.kt):
//   {x:{type:'center'|'top'|...}, y:{type:...}}  |  {x:{type:'percentage', value}, y:...}
//   |  {x:{type:'length', px}, y:...}
export interface PerspectiveOriginConfig { value?: string; }
export const PERSPECTIVE_ORIGIN_PROPERTY_TYPE = 'PerspectiveOrigin' as const;
export type PerspectiveOriginPropertyType = typeof PERSPECTIVE_ORIGIN_PROPERTY_TYPE;
