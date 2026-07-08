// MaskTypeConfig.ts — SVG presentation attribute `mask-type`.  Values: luminance|alpha.
export interface MaskTypeConfig { value?: string; }
export const MASK_TYPE_PROPERTY_TYPE = 'MaskType' as const;
export type MaskTypePropertyType = typeof MASK_TYPE_PROPERTY_TYPE;
