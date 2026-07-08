// BorderBottomStyleConfig.ts — typed record for the `border-bottom-style` IR property.
// Mirrors src/main/kotlin/app/parsing/css/properties/longhands/borders/sides/BorderBottomStylePropertyParser.kt on the parser side — shape parity keeps every
// side's pipeline uniform so the renderer dispatches them without casing.



// Single-field config — style may be absent when the property isn't set.
export interface BorderBottomStyleConfig {
  style?: string;                                                         // CSS border-style keyword (lowercased)
}

// IR property type string — used by both extractor + registry.
export const BORDER_BOTTOM_STYLE_PROPERTY_TYPE = 'BorderBottomStyle' as const;
export type BorderBottomStylePropertyType = typeof BORDER_BOTTOM_STYLE_PROPERTY_TYPE;
