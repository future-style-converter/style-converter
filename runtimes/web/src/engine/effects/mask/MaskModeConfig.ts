// MaskModeConfig.ts — CSS `mask-mode`.  Values: alpha|luminance|match-source.
// IR emits {type:'app.irmodels.properties.effects.MaskModeValue.Alpha'} etc.
export interface MaskModeConfig { value?: string; }
export const MASK_MODE_PROPERTY_TYPE = 'MaskMode' as const;
export type MaskModePropertyType = typeof MASK_MODE_PROPERTY_TYPE;
