// BorderBlockStartStyleConfig.ts — typed record for the `border-block-start-style` IR property.
// Mirrors src/main/kotlin/app/parsing/css/properties/longhands/borders/sides/BorderBlockStartStylePropertyParser.kt on the parser side — shape parity keeps every
// side's pipeline uniform so the renderer dispatches them without casing.



// Single-field config — style may be absent when the property isn't set.
export interface BorderBlockStartStyleConfig {
  style?: string;                                                         // CSS border-style keyword (lowercased)
}

// IR property type string — used by both extractor + registry.
export const BORDER_BLOCK_START_STYLE_PROPERTY_TYPE = 'BorderBlockStartStyle' as const;
export type BorderBlockStartStylePropertyType = typeof BORDER_BLOCK_START_STYLE_PROPERTY_TYPE;
