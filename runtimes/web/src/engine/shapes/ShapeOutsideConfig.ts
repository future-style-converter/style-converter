// ShapeOutsideConfig.ts — https://developer.mozilla.org/docs/Web/CSS/shape-outside
// Web is a PASSTHROUGH platform for float exclusion: the browser owns the
// exclusion geometry, so the whole job is emitting the declaration verbatim.
// IR wire (converter …/irmodels/properties/shapes/ShapeOutsideProperty.kt):
//   {type:'none'}                             -> 'none'
//   {type:'margin-box'|'border-box'
//        |'padding-box'|'content-box'}        -> the <shape-box> keyword
//   {type:'basic-shape', shape:'circle(…)'}   -> the declaration text verbatim
//                                                (may carry a trailing box)
//   {type:'image-url', url:'shape.png'}       -> url("shape.png")
//   {type:'keyword', keyword:'inherit'}       -> the CSS-wide keyword
//   {type:'raw', value:'linear-gradient(…)'}  -> the value verbatim
export interface ShapeOutsideConfig { value?: string }
export const SHAPEOUTSIDE_PROPERTY_TYPE = 'ShapeOutside' as const;
