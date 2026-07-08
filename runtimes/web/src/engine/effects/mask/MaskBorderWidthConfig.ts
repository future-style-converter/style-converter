// MaskBorderWidthConfig.ts — CSS `mask-border-width`.
// IR shapes (observed in mask-border-width fixture):
//   {type:'auto'}                             -> 'auto'
//   {type:'length', px:N}                     -> '<N>px'
//   {type:'number', value:N}                  -> '<N>'  (unitless multiplier)
//   {type:'multi', top,right,bottom,left}     -> 'T R B L' (each already a CSS string)
export interface MaskBorderWidthConfig { value?: string; }
export const MASK_BORDER_WIDTH_PROPERTY_TYPE = 'MaskBorderWidth' as const;
export type MaskBorderWidthPropertyType = typeof MASK_BORDER_WIDTH_PROPERTY_TYPE;
