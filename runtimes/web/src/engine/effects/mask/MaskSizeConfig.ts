// MaskSizeConfig.ts — CSS `mask-size`.  Grammar: [ <length-percentage> | auto ]{1,2} | cover | contain.
// IR shape: {width:{type:'auto'|'cover'|'contain'|'length', …}, height:{…}}.
export interface MaskSizeConfig { value?: string; }
export const MASK_SIZE_PROPERTY_TYPE = 'MaskSize' as const;
export type MaskSizePropertyType = typeof MASK_SIZE_PROPERTY_TYPE;
