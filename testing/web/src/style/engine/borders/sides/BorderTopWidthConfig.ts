// BorderTopWidthConfig.ts — typed record for the `border-top-width` IR property.
// Shape parity with every other Border*Width config so the dispatch code in
// StyleBuilder can treat them uniformly.  The CSS parser that feeds us lives at
// src/main/kotlin/app/parsing/css/properties/longhands/borders/sides/BorderTopWidthPropertyParser.kt.

// Reuse the Phase-1 LengthValue alphabet — px/em/rem/calc/percent all supported.
import type { LengthValue } from '../../core/types/LengthValue';

// Single-field config — width may be absent when the property isn't set.
export interface BorderTopWidthConfig {
  width?: LengthValue;                                                    // parsed IR length or undefined
}

// IR property type string — used by both extractor + registry.
export const BORDER_TOP_WIDTH_PROPERTY_TYPE = 'BorderTopWidth' as const;
export type BorderTopWidthPropertyType = typeof BORDER_TOP_WIDTH_PROPERTY_TYPE;
