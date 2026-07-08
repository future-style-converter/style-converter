// BorderInlineStartStyleConfig.ts — typed record for the `border-inline-start-style` IR property.
// Mirrors src/main/kotlin/app/parsing/css/properties/longhands/borders/sides/BorderInlineStartStylePropertyParser.kt on the parser side — shape parity keeps every
// side's pipeline uniform so the renderer dispatches them without casing.



// Single-field config — style may be absent when the property isn't set.
export interface BorderInlineStartStyleConfig {
  style?: string;                                                         // CSS border-style keyword (lowercased)
}

// IR property type string — used by both extractor + registry.
export const BORDER_INLINE_START_STYLE_PROPERTY_TYPE = 'BorderInlineStartStyle' as const;
export type BorderInlineStartStylePropertyType = typeof BORDER_INLINE_START_STYLE_PROPERTY_TYPE;
