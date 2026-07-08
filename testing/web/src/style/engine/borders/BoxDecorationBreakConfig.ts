// BoxDecorationBreakConfig.ts — typed record for the `box-decoration-break`
// IR property.  Mirrors src/main/kotlin/app/irmodels/properties/borders/BoxDecorationBreakProperty.kt.
// Property controls how borders/padding are rendered across fragmented boxes
// (line breaks, column breaks, page breaks).  CSS Fragmentation §5.

// Single-field config — `value` absent means the property isn't set.
export interface BoxDecorationBreakConfig {
  value?: 'slice' | 'clone';                                               // the two CSS keywords
}

// IR property type string — used by both extractor + registry.
export const BOX_DECORATION_BREAK_PROPERTY_TYPE = 'BoxDecorationBreak' as const;
export type BoxDecorationBreakPropertyType = typeof BOX_DECORATION_BREAK_PROPERTY_TYPE;
