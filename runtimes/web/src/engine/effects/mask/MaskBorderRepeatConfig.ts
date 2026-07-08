// MaskBorderRepeatConfig.ts — CSS `mask-border-repeat`.
// IR shapes:
//   {type:'stretch'|'repeat'|'round'|'space'}            -> single keyword
//   {type:'two-value', horizontal, vertical}             -> '<H> <V>'
export interface MaskBorderRepeatConfig { value?: string; }
export const MASK_BORDER_REPEAT_PROPERTY_TYPE = 'MaskBorderRepeat' as const;
export type MaskBorderRepeatPropertyType = typeof MASK_BORDER_REPEAT_PROPERTY_TYPE;
