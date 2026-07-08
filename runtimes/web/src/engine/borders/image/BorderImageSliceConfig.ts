// BorderImageSliceConfig.ts — typed record for the `border-image-slice` IR property.
// Mirrors src/main/kotlin/app/parsing/css/properties/longhands/borders/image/BorderImageSlicePropertyParser.kt.

import type { QuadEdge } from './_shared';

// Single-field config — `quad` is absent when the property isn't set.
export interface BorderImageSliceConfig {
  quad?: QuadEdge;                                               // parsed IR value
}

// IR property type string — used by both extractor + registry.
export const BORDER_IMAGE_SLICE_PROPERTY_TYPE = 'BorderImageSlice' as const;
export type BorderImageSlicePropertyType = typeof BORDER_IMAGE_SLICE_PROPERTY_TYPE;
