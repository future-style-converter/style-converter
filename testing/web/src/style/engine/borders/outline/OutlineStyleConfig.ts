// OutlineStyleConfig.ts — typed record for the `outline-style` IR property.
// Mirrors src/main/kotlin/app/parsing/css/properties/longhands/appearance/OutlineStylePropertyParser.kt.



// Single-field config — `style` is absent when the property isn't set.
export interface OutlineStyleConfig {
  style?: string;                                                // parsed IR value
}

// IR property type string — used by both extractor + registry.
export const OUTLINE_STYLE_PROPERTY_TYPE = 'OutlineStyle' as const;
export type OutlineStylePropertyType = typeof OUTLINE_STYLE_PROPERTY_TYPE;
