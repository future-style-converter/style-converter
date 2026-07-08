// BorderBottomRightRadiusConfig.ts — typed record for the `border-bottom-right-radius` IR property.
// Mirrors src/main/kotlin/app/parsing/css/properties/longhands/borders/radius/BorderBottomRightRadiusPropertyParser.kt.  The value is a BorderRadiusValue which covers both
// the circular (single length) and elliptical (horizontal/vertical pair) CSS forms.

import type { BorderRadiusValue } from './_shared';                     // shared value union

// Single-field config — `radius` absent means the property isn't set.
export interface BorderBottomRightRadiusConfig {
  radius?: BorderRadiusValue;                                             // circle or ellipse
}

// IR property type string — used by both extractor + registry.
export const BORDER_BOTTOM_RIGHT_RADIUS_PROPERTY_TYPE = 'BorderBottomRightRadius' as const;
export type BorderBottomRightRadiusPropertyType = typeof BORDER_BOTTOM_RIGHT_RADIUS_PROPERTY_TYPE;
