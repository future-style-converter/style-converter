// BorderTopRightRadiusConfig.ts — typed record for the `border-top-right-radius` IR property.
// Mirrors src/main/kotlin/app/parsing/css/properties/longhands/borders/radius/BorderTopRightRadiusPropertyParser.kt.  The value is a BorderRadiusValue which covers both
// the circular (single length) and elliptical (horizontal/vertical pair) CSS forms.

import type { BorderRadiusValue } from './_shared';                     // shared value union

// Single-field config — `radius` absent means the property isn't set.
export interface BorderTopRightRadiusConfig {
  radius?: BorderRadiusValue;                                             // circle or ellipse
}

// IR property type string — used by both extractor + registry.
export const BORDER_TOP_RIGHT_RADIUS_PROPERTY_TYPE = 'BorderTopRightRadius' as const;
export type BorderTopRightRadiusPropertyType = typeof BORDER_TOP_RIGHT_RADIUS_PROPERTY_TYPE;
