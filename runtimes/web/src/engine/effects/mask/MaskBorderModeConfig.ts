// MaskBorderModeConfig.ts — CSS `mask-border-mode`.  Values: luminance|alpha.
export interface MaskBorderModeConfig { value?: string; }
export const MASK_BORDER_MODE_PROPERTY_TYPE = 'MaskBorderMode' as const;
export type MaskBorderModePropertyType = typeof MASK_BORDER_MODE_PROPERTY_TYPE;
