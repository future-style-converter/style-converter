// ShapeInsideConfig.ts — https://developer.mozilla.org/docs/Web/CSS/shape-inside
// Emitted natively (CSS Shapes 2); no shipping browser implements it yet, so
// the declaration is inert today — we still decode every wire variant so the
// value is honest the day a browser turns it on.
// IR wire (converter …/irmodels/properties/shapes/ShapeInsideProperty.kt):
//   {type:'auto'}                       -> 'auto'
//   {type:'none'}                       -> 'none'
//   {type:'shape', shape:'circle(…)'}   -> the declaration text verbatim
export interface ShapeInsideConfig { value?: string }
