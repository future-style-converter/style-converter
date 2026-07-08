// MaskBorderOutsetConfig.ts — CSS `mask-border-outset`.
// Same IR shape set as MaskBorderWidth minus 'auto'.
export interface MaskBorderOutsetConfig { value?: string; }
export const MASK_BORDER_OUTSET_PROPERTY_TYPE = 'MaskBorderOutset' as const;
export type MaskBorderOutsetPropertyType = typeof MASK_BORDER_OUTSET_PROPERTY_TYPE;
