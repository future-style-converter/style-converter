// BorderInlineEndWidthConfig.ts — typed record for the `border-inline-end-width` IR property.
// Mirrors src/main/kotlin/app/parsing/css/properties/longhands/borders/sides/BorderInlineEndWidthPropertyParser.kt on the parser side — shape parity keeps every
// side's pipeline uniform so the renderer dispatches them without casing.

import type { LengthValue } from '../../core/types/LengthValue';

// Single-field config — width may be absent when the property isn't set.
export interface BorderInlineEndWidthConfig {
  width?: LengthValue;                                                    // parsed IR length or undefined
}

// IR property type string — used by both extractor + registry.
export const BORDER_INLINE_END_WIDTH_PROPERTY_TYPE = 'BorderInlineEndWidth' as const;
export type BorderInlineEndWidthPropertyType = typeof BORDER_INLINE_END_WIDTH_PROPERTY_TYPE;
