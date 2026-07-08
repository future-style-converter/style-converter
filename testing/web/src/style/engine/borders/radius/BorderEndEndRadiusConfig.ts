// BorderEndEndRadiusConfig.ts — typed record for the `border-end-end-radius` IR property.
// Mirrors src/main/kotlin/app/parsing/css/properties/longhands/borders/radius/BorderEndEndRadiusPropertyParser.kt.  The value is a BorderRadiusValue which covers both
// the circular (single length) and elliptical (horizontal/vertical pair) CSS forms.

import type { BorderRadiusValue } from './_shared';                     // shared value union

// Single-field config — `radius` absent means the property isn't set.
export interface BorderEndEndRadiusConfig {
  radius?: BorderRadiusValue;                                             // circle or ellipse
}

// IR property type string — used by both extractor + registry.
export const BORDER_END_END_RADIUS_PROPERTY_TYPE = 'BorderEndEndRadius' as const;
export type BorderEndEndRadiusPropertyType = typeof BORDER_END_END_RADIUS_PROPERTY_TYPE;
