// MaskPositionXConfig.ts — CSS `mask-position-x` (Masking 1 §4.3 longhand).
// https://developer.mozilla.org/docs/Web/CSS/mask-position-x
export interface MaskPositionXConfig { value?: string; }
export const MASK_POSITION_X_PROPERTY_TYPE = 'MaskPositionX' as const;
export type MaskPositionXPropertyType = typeof MASK_POSITION_X_PROPERTY_TYPE;
