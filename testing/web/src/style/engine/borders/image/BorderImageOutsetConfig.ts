// BorderImageOutsetConfig.ts — typed record for the `border-image-outset` IR property.
// Mirrors src/main/kotlin/app/parsing/css/properties/longhands/borders/image/BorderImageOutsetPropertyParser.kt.

import type { QuadEdge } from './_shared';

// Single-field config — `quad` is absent when the property isn't set.
export interface BorderImageOutsetConfig {
  quad?: QuadEdge;                                               // parsed IR value
}

// IR property type string — used by both extractor + registry.
export const BORDER_IMAGE_OUTSET_PROPERTY_TYPE = 'BorderImageOutset' as const;
export type BorderImageOutsetPropertyType = typeof BORDER_IMAGE_OUTSET_PROPERTY_TYPE;
