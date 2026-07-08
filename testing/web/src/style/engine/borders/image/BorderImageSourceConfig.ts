// BorderImageSourceConfig.ts — typed record for the `border-image-source` IR property.
// Mirrors src/main/kotlin/app/parsing/css/properties/longhands/borders/image/BorderImageSourcePropertyParser.kt.

import type { BorderImageSourceValue } from './_shared';

// Single-field config — `source` is absent when the property isn't set.
export interface BorderImageSourceConfig {
  source?: BorderImageSourceValue;                                               // parsed IR value
}

// IR property type string — used by both extractor + registry.
export const BORDER_IMAGE_SOURCE_PROPERTY_TYPE = 'BorderImageSource' as const;
export type BorderImageSourcePropertyType = typeof BORDER_IMAGE_SOURCE_PROPERTY_TYPE;
