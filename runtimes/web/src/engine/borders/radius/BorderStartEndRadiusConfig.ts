// BorderStartEndRadiusConfig.ts — typed record for the `border-start-end-radius` IR property.
// Mirrors src/main/kotlin/app/parsing/css/properties/longhands/borders/radius/BorderStartEndRadiusPropertyParser.kt.  The value is a BorderRadiusValue which covers both
// the circular (single length) and elliptical (horizontal/vertical pair) CSS forms.

import type { BorderRadiusValue } from './_shared';                     // shared value union

// Single-field config — `radius` absent means the property isn't set.
export interface BorderStartEndRadiusConfig {
  radius?: BorderRadiusValue;                                             // circle or ellipse
}

// IR property type string — used by both extractor + registry.
export const BORDER_START_END_RADIUS_PROPERTY_TYPE = 'BorderStartEndRadius' as const;
export type BorderStartEndRadiusPropertyType = typeof BORDER_START_END_RADIUS_PROPERTY_TYPE;
