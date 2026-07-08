// BorderBlockEndColorConfig.ts — typed record for the `border-block-end-color` IR property.
// Mirrors src/main/kotlin/app/parsing/css/properties/longhands/borders/sides/BorderBlockEndColorPropertyParser.kt on the parser side — shape parity keeps every
// side's pipeline uniform so the renderer dispatches them without casing.

import type { ColorValue } from '../../core/types/ColorValue';

// Single-field config — color may be absent when the property isn't set.
export interface BorderBlockEndColorConfig {
  color?: ColorValue;                                                     // parsed color (static or dynamic)
}

// IR property type string — used by both extractor + registry.
export const BORDER_BLOCK_END_COLOR_PROPERTY_TYPE = 'BorderBlockEndColor' as const;
export type BorderBlockEndColorPropertyType = typeof BORDER_BLOCK_END_COLOR_PROPERTY_TYPE;
