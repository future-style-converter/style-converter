// BorderEndStartRadiusConfig.ts — typed record for the `border-end-start-radius` IR property.
// Mirrors src/main/kotlin/app/parsing/css/properties/longhands/borders/radius/BorderEndStartRadiusPropertyParser.kt.  The value is a BorderRadiusValue which covers both
// the circular (single length) and elliptical (horizontal/vertical pair) CSS forms.

import type { BorderRadiusValue } from './_shared';                     // shared value union

// Single-field config — `radius` absent means the property isn't set.
export interface BorderEndStartRadiusConfig {
  radius?: BorderRadiusValue;                                             // circle or ellipse
}

// IR property type string — used by both extractor + registry.
export const BORDER_END_START_RADIUS_PROPERTY_TYPE = 'BorderEndStartRadius' as const;
export type BorderEndStartRadiusPropertyType = typeof BORDER_END_START_RADIUS_PROPERTY_TYPE;
