// BorderLeftStyleConfig.ts — typed record for the `border-left-style` IR property.
// Mirrors src/main/kotlin/app/parsing/css/properties/longhands/borders/sides/BorderLeftStylePropertyParser.kt on the parser side — shape parity keeps every
// side's pipeline uniform so the renderer dispatches them without casing.



// Single-field config — style may be absent when the property isn't set.
export interface BorderLeftStyleConfig {
  style?: string;                                                         // CSS border-style keyword (lowercased)
}

// IR property type string — used by both extractor + registry.
export const BORDER_LEFT_STYLE_PROPERTY_TYPE = 'BorderLeftStyle' as const;
export type BorderLeftStylePropertyType = typeof BORDER_LEFT_STYLE_PROPERTY_TYPE;
