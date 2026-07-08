// BorderInlineEndStyleConfig.ts — typed record for the `border-inline-end-style` IR property.
// Mirrors src/main/kotlin/app/parsing/css/properties/longhands/borders/sides/BorderInlineEndStylePropertyParser.kt on the parser side — shape parity keeps every
// side's pipeline uniform so the renderer dispatches them without casing.



// Single-field config — style may be absent when the property isn't set.
export interface BorderInlineEndStyleConfig {
  style?: string;                                                         // CSS border-style keyword (lowercased)
}

// IR property type string — used by both extractor + registry.
export const BORDER_INLINE_END_STYLE_PROPERTY_TYPE = 'BorderInlineEndStyle' as const;
export type BorderInlineEndStylePropertyType = typeof BORDER_INLINE_END_STYLE_PROPERTY_TYPE;
