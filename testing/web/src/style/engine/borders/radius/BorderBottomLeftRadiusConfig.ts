// BorderBottomLeftRadiusConfig.ts — typed record for the `border-bottom-left-radius` IR property.
// Mirrors src/main/kotlin/app/parsing/css/properties/longhands/borders/radius/BorderBottomLeftRadiusPropertyParser.kt.  The value is a BorderRadiusValue which covers both
// the circular (single length) and elliptical (horizontal/vertical pair) CSS forms.

import type { BorderRadiusValue } from './_shared';                     // shared value union

// Single-field config — `radius` absent means the property isn't set.
export interface BorderBottomLeftRadiusConfig {
  radius?: BorderRadiusValue;                                             // circle or ellipse
}

// IR property type string — used by both extractor + registry.
export const BORDER_BOTTOM_LEFT_RADIUS_PROPERTY_TYPE = 'BorderBottomLeftRadius' as const;
export type BorderBottomLeftRadiusPropertyType = typeof BORDER_BOTTOM_LEFT_RADIUS_PROPERTY_TYPE;
