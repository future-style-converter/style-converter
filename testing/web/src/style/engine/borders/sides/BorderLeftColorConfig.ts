// BorderLeftColorConfig.ts — typed record for the `border-left-color` IR property.
// Mirrors src/main/kotlin/app/parsing/css/properties/longhands/borders/sides/BorderLeftColorPropertyParser.kt on the parser side — shape parity keeps every
// side's pipeline uniform so the renderer dispatches them without casing.

import type { ColorValue } from '../../core/types/ColorValue';

// Single-field config — color may be absent when the property isn't set.
export interface BorderLeftColorConfig {
  color?: ColorValue;                                                     // parsed color (static or dynamic)
}

// IR property type string — used by both extractor + registry.
export const BORDER_LEFT_COLOR_PROPERTY_TYPE = 'BorderLeftColor' as const;
export type BorderLeftColorPropertyType = typeof BORDER_LEFT_COLOR_PROPERTY_TYPE;
