// MaskOriginConfig.ts — CSS `mask-origin`.  Values: content-box|padding-box|border-box|fill-box|stroke-box|view-box.
export interface MaskOriginConfig { value?: string; }
export const MASK_ORIGIN_PROPERTY_TYPE = 'MaskOrigin' as const;
export type MaskOriginPropertyType = typeof MASK_ORIGIN_PROPERTY_TYPE;
