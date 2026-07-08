// RotateConfig.ts — CSS `rotate` longhand (CSS Transforms L2).
// https://developer.mozilla.org/docs/Web/CSS/rotate
// IR shape (RotatePropertyParser.kt):
//   { type:'none' }                              -> 'none'
//   { type:'angle', deg:N, original?:{v,u} }     -> '<deg>deg'
//   { type:'axis-angle', x,y,z, angle:{deg} }    -> '<x> <y> <z> <deg>deg'

export interface RotateConfig { value?: string; }                                 // pre-serialised CSS
export const ROTATE_PROPERTY_TYPE = 'Rotate' as const;
export type RotatePropertyType = typeof ROTATE_PROPERTY_TYPE;
