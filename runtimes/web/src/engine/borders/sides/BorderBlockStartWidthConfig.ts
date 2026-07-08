// BorderBlockStartWidthConfig.ts — typed record for the `border-block-start-width` IR property.
// Mirrors src/main/kotlin/app/parsing/css/properties/longhands/borders/sides/BorderBlockStartWidthPropertyParser.kt on the parser side — shape parity keeps every
// side's pipeline uniform so the renderer dispatches them without casing.

import type { LengthValue } from '../../core/types/LengthValue';

// Single-field config — width may be absent when the property isn't set.
export interface BorderBlockStartWidthConfig {
  width?: LengthValue;                                                    // parsed IR length or undefined
}

// IR property type string — used by both extractor + registry.
export const BORDER_BLOCK_START_WIDTH_PROPERTY_TYPE = 'BorderBlockStartWidth' as const;
export type BorderBlockStartWidthPropertyType = typeof BORDER_BLOCK_START_WIDTH_PROPERTY_TYPE;
