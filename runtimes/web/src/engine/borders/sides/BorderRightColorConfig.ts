// BorderRightColorConfig.ts — typed record for the `border-right-color` IR property.
// Mirrors src/main/kotlin/app/parsing/css/properties/longhands/borders/sides/BorderRightColorPropertyParser.kt on the parser side — shape parity keeps every
// side's pipeline uniform so the renderer dispatches them without casing.

import type { ColorValue } from '../../core/types/ColorValue';

// Single-field config — color may be absent when the property isn't set.
export interface BorderRightColorConfig {
  color?: ColorValue;                                                     // parsed color (static or dynamic)
}

// IR property type string — used by both extractor + registry.
export const BORDER_RIGHT_COLOR_PROPERTY_TYPE = 'BorderRightColor' as const;
export type BorderRightColorPropertyType = typeof BORDER_RIGHT_COLOR_PROPERTY_TYPE;
