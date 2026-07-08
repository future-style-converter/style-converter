// BorderInlineStartColorConfig.ts — typed record for the `border-inline-start-color` IR property.
// Mirrors src/main/kotlin/app/parsing/css/properties/longhands/borders/sides/BorderInlineStartColorPropertyParser.kt on the parser side — shape parity keeps every
// side's pipeline uniform so the renderer dispatches them without casing.

import type { ColorValue } from '../../core/types/ColorValue';

// Single-field config — color may be absent when the property isn't set.
export interface BorderInlineStartColorConfig {
  color?: ColorValue;                                                     // parsed color (static or dynamic)
}

// IR property type string — used by both extractor + registry.
export const BORDER_INLINE_START_COLOR_PROPERTY_TYPE = 'BorderInlineStartColor' as const;
export type BorderInlineStartColorPropertyType = typeof BORDER_INLINE_START_COLOR_PROPERTY_TYPE;
