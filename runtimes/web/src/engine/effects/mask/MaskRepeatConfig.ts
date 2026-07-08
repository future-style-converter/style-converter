// MaskRepeatConfig.ts — CSS `mask-repeat`.  Values: repeat|no-repeat|repeat-x|repeat-y|round|space.
// IR emits {type:'…MaskRepeatValue.Repeat'} etc.
export interface MaskRepeatConfig { value?: string; }
export const MASK_REPEAT_PROPERTY_TYPE = 'MaskRepeat' as const;
export type MaskRepeatPropertyType = typeof MASK_REPEAT_PROPERTY_TYPE;
