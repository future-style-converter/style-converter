// BorderTopLeftRadiusConfig.ts — typed record for the `border-top-left-radius` IR property.
// Mirrors src/main/kotlin/app/parsing/css/properties/longhands/borders/radius/BorderTopLeftRadiusPropertyParser.kt.  The value is a BorderRadiusValue which covers both
// the circular (single length) and elliptical (horizontal/vertical pair) CSS forms.

import type { BorderRadiusValue } from './_shared';                     // shared value union

// Single-field config — `radius` absent means the property isn't set.
export interface BorderTopLeftRadiusConfig {
  radius?: BorderRadiusValue;                                             // circle or ellipse
}

// IR property type string — used by both extractor + registry.
export const BORDER_TOP_LEFT_RADIUS_PROPERTY_TYPE = 'BorderTopLeftRadius' as const;
export type BorderTopLeftRadiusPropertyType = typeof BORDER_TOP_LEFT_RADIUS_PROPERTY_TYPE;
