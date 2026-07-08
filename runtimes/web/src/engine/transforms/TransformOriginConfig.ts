// TransformOriginConfig.ts — CSS `transform-origin` (Transforms L1).
// https://developer.mozilla.org/docs/Web/CSS/transform-origin
// IR shape (TransformOriginPropertyParser.kt):
//   {x:{type:'keyword', value:'CENTER'}, y:{type:'keyword', value:'CENTER'}}
//   {x:{type:'percentage', percentage:50}, y:...}
//   {x:{type:'length', px:20}, y:..., z?:{px:10}}

export interface TransformOriginConfig { value?: string; }
export const TRANSFORM_ORIGIN_PROPERTY_TYPE = 'TransformOrigin' as const;
export type TransformOriginPropertyType = typeof TRANSFORM_ORIGIN_PROPERTY_TYPE;
