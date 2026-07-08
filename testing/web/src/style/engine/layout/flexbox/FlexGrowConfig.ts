// FlexGrowConfig.ts — typed config for CSS `flex-grow`.
// See parsing/css/properties/longhands/layout/flexbox/FlexGrowPropertyParser.kt
// for the IR value flavours (number + `initial`/`inherit` keywords).

// Single number, or a pre-baked keyword if the IR arrived as such.  Undefined
// means the property is absent from the IR — applier emits nothing.
export interface FlexGrowConfig {
  value?: number | string;
}

export const FLEX_GROW_PROPERTY_TYPE = 'FlexGrow' as const;
export type FlexGrowPropertyType = typeof FLEX_GROW_PROPERTY_TYPE;
