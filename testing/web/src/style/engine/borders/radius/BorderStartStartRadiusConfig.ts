// BorderStartStartRadiusConfig.ts — typed record for the `border-start-start-radius` IR property.
// Mirrors src/main/kotlin/app/parsing/css/properties/longhands/borders/radius/BorderStartStartRadiusPropertyParser.kt.  The value is a BorderRadiusValue which covers both
// the circular (single length) and elliptical (horizontal/vertical pair) CSS forms.

import type { BorderRadiusValue } from './_shared';                     // shared value union

// Single-field config — `radius` absent means the property isn't set.
export interface BorderStartStartRadiusConfig {
  radius?: BorderRadiusValue;                                             // circle or ellipse
}

// IR property type string — used by both extractor + registry.
export const BORDER_START_START_RADIUS_PROPERTY_TYPE = 'BorderStartStartRadius' as const;
export type BorderStartStartRadiusPropertyType = typeof BORDER_START_START_RADIUS_PROPERTY_TYPE;
