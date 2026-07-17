// BorderImageSliceConfig.ts — typed record for the `border-image-slice` IR property.
// Mirrors src/main/kotlin/app/parsing/css/properties/longhands/borders/image/BorderImageSlicePropertyParser.kt.

import type { QuadEdge } from './_shared';

// Config — `quad` is absent when the property isn't set; `fill` mirrors the
// optional `fill` keyword of the css-backgrounds-3 §6.1 grammar
// (`<number-percentage>{1,4} && fill?`) which preserves the middle region.
export interface BorderImageSliceConfig {
  quad?: QuadEdge;                                               // parsed IR value
  fill?: boolean;                                                // true when IR carried fill=true
}

// IR property type string — used by both extractor + registry.
export const BORDER_IMAGE_SLICE_PROPERTY_TYPE = 'BorderImageSlice' as const;
export type BorderImageSlicePropertyType = typeof BORDER_IMAGE_SLICE_PROPERTY_TYPE;
