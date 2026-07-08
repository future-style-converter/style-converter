// OutlineOffsetConfig.ts — typed record for the `outline-offset` IR property.
// Mirrors src/main/kotlin/app/parsing/css/properties/longhands/appearance/OutlineOffsetPropertyParser.kt.

import type { LengthValue } from '../../core/types/LengthValue';

// Single-field config — `offset` is absent when the property isn't set.
export interface OutlineOffsetConfig {
  offset?: LengthValue;                                                // parsed IR value
}

// IR property type string — used by both extractor + registry.
export const OUTLINE_OFFSET_PROPERTY_TYPE = 'OutlineOffset' as const;
export type OutlineOffsetPropertyType = typeof OUTLINE_OFFSET_PROPERTY_TYPE;
