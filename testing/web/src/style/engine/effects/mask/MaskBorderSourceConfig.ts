// MaskBorderSourceConfig.ts — CSS `mask-border-source` (Masking L1 §7.1).
// https://developer.mozilla.org/docs/Web/CSS/mask-border-source
// IR: bare string (empty means 'none', otherwise URL reference).
export interface MaskBorderSourceConfig { value?: string; }
export const MASK_BORDER_SOURCE_PROPERTY_TYPE = 'MaskBorderSource' as const;
export type MaskBorderSourcePropertyType = typeof MASK_BORDER_SOURCE_PROPERTY_TYPE;
