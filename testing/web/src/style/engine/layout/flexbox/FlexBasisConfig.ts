// FlexBasisConfig.ts — CSS `flex-basis`.
// The IR supports: auto | content | max-content | min-content | fit-content |
// <length> | <percentage> | calc() expr | bare keyword (see
// FlexBasisPropertyParser.kt).  Web accepts all of those verbatim — the config
// just carries a ready-to-emit CSS string.

export interface FlexBasisConfig { value?: string; }

export const FLEX_BASIS_PROPERTY_TYPE = 'FlexBasis' as const;
export type FlexBasisPropertyType = typeof FLEX_BASIS_PROPERTY_TYPE;
