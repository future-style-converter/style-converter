// MaskClipConfig.ts — CSS `mask-clip`.  Same box set as MaskOrigin plus 'no-clip'.
export interface MaskClipConfig { value?: string; }
export const MASK_CLIP_PROPERTY_TYPE = 'MaskClip' as const;
export type MaskClipPropertyType = typeof MASK_CLIP_PROPERTY_TYPE;
