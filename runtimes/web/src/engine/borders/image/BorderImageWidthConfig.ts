// BorderImageWidthConfig.ts — typed record for the `border-image-width` IR property.
// Mirrors src/main/kotlin/app/parsing/css/properties/longhands/borders/image/BorderImageWidthPropertyParser.kt.

import type { QuadEdge } from './_shared';

// Single-field config — `quad` is absent when the property isn't set.
export interface BorderImageWidthConfig {
  quad?: QuadEdge;                                               // parsed IR value
}

// IR property type string — used by both extractor + registry.
export const BORDER_IMAGE_WIDTH_PROPERTY_TYPE = 'BorderImageWidth' as const;
export type BorderImageWidthPropertyType = typeof BORDER_IMAGE_WIDTH_PROPERTY_TYPE;
