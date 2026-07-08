// MaskCompositeConfig.ts — CSS `mask-composite`.  Values: add|subtract|intersect|exclude.
// IR emits FQCN {type:'…MaskCompositeValue.Add'} etc.
export interface MaskCompositeConfig { value?: string; }
export const MASK_COMPOSITE_PROPERTY_TYPE = 'MaskComposite' as const;
export type MaskCompositePropertyType = typeof MASK_COMPOSITE_PROPERTY_TYPE;
