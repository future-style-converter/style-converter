// FlexShrinkConfig.ts — typed config for CSS `flex-shrink`.
// Same IR shape as FlexGrow (see FlexShrinkPropertyParser.kt) — a Number sealed
// subclass holding a single float.  Value undefined means "property absent".

export interface FlexShrinkConfig { value?: number | string; }

export const FLEX_SHRINK_PROPERTY_TYPE = 'FlexShrink' as const;
export type FlexShrinkPropertyType = typeof FLEX_SHRINK_PROPERTY_TYPE;
