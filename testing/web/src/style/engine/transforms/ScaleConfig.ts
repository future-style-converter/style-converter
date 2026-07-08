// ScaleConfig.ts — CSS `scale` longhand (CSS Transforms L2).
// https://developer.mozilla.org/docs/Web/CSS/scale
// IR shape (ScalePropertyParser.kt):
//   {type:'none'}                        -> 'none'
//   {type:'uniform', value:N}            -> '<N>'
//   {type:'2d', x:N, y:N}                -> '<x> <y>'
//   {type:'3d', x:N, y:N, z:N}           -> '<x> <y> <z>'

export interface ScaleConfig { value?: string; }
export const SCALE_PROPERTY_TYPE = 'Scale' as const;
export type ScalePropertyType = typeof SCALE_PROPERTY_TYPE;
