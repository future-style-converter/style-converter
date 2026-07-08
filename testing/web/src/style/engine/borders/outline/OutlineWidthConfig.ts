// OutlineWidthConfig.ts — typed record for the `outline-width` IR property.
// Mirrors src/main/kotlin/app/parsing/css/properties/longhands/appearance/OutlineWidthPropertyParser.kt.

import type { LengthValue } from '../../core/types/LengthValue';

// Single-field config — `width` is absent when the property isn't set.
export interface OutlineWidthConfig {
  width?: LengthValue;                                                // parsed IR value
}

// IR property type string — used by both extractor + registry.
export const OUTLINE_WIDTH_PROPERTY_TYPE = 'OutlineWidth' as const;
export type OutlineWidthPropertyType = typeof OUTLINE_WIDTH_PROPERTY_TYPE;
