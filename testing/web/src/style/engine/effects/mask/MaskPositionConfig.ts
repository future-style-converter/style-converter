// MaskPositionConfig.ts — CSS `mask-position`.  Layer list of <position>.
// IR shape: [{x:{type:..}, y:{type:..}}, ...] — one entry per layer.
export interface MaskPositionConfig { value?: string; }                            // comma-joined layer values
export const MASK_POSITION_PROPERTY_TYPE = 'MaskPosition' as const;
export type MaskPositionPropertyType = typeof MASK_POSITION_PROPERTY_TYPE;
