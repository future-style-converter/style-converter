// MaskImageConfig.ts — CSS `mask-image` (CSS Masking 1 §4).
// https://developer.mozilla.org/docs/Web/CSS/mask-image
// IR emits an array of layers identical in shape to BackgroundImage:
//   ['none']                                         -> 'none'
//   ['mask.png']                                     -> 'url("mask.png")'
//   [{type:'linear-gradient', angle, stops}, ...]    -> gradient list
export interface MaskImageConfig { value?: string; }                                // comma-joined layers
export const MASK_IMAGE_PROPERTY_TYPE = 'MaskImage' as const;
export type MaskImagePropertyType = typeof MASK_IMAGE_PROPERTY_TYPE;
