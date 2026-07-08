// OutlineColorConfig.ts — typed record for the `outline-color` IR property.
// Mirrors src/main/kotlin/app/parsing/css/properties/longhands/appearance/OutlineColorPropertyParser.kt.

import type { ColorValue } from '../../core/types/ColorValue';

// Single-field config — `color` is absent when the property isn't set.
export interface OutlineColorConfig {
  color?: ColorValue;                                                // parsed IR value
}

// IR property type string — used by both extractor + registry.
export const OUTLINE_COLOR_PROPERTY_TYPE = 'OutlineColor' as const;
export type OutlineColorPropertyType = typeof OUTLINE_COLOR_PROPERTY_TYPE;
