// MaskBorderSliceConfig.ts — CSS `mask-border-slice`.
// https://developer.mozilla.org/docs/Web/CSS/mask-border-slice
// IR shapes:
//   N (number)                                                -> 'N'
//   'fill'                                                    -> 'fill'
//   {top:N|{pct:N}, right:…, bottom:…, left:…, fill?:true}    -> 'T R B L[ fill]'
export interface MaskBorderSliceConfig { value?: string; }
export const MASK_BORDER_SLICE_PROPERTY_TYPE = 'MaskBorderSlice' as const;
export type MaskBorderSlicePropertyType = typeof MASK_BORDER_SLICE_PROPERTY_TYPE;
