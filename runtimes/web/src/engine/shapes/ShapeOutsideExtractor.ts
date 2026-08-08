// ShapeOutsideExtractor.ts — folds IR `ShapeOutside` nodes to a CSS string.
// The wire is a Kotlin sealed interface (ShapeOutsideProperty.kt), NOT the
// flat keyword/raw shape `_phase10_shared.keywordOrRaw` understands, so the
// decoding lives in the category's own `_shared.ts` (see that file's header
// for why every basic shape and every <shape-box> used to be dropped).
import { foldLast, type IRPropertyLike } from '../_phase10_shared';
import { shapeValueToCss } from './_shared';
import type { ShapeOutsideConfig } from './ShapeOutsideConfig';
export function extractShapeOutside(properties: IRPropertyLike[]): ShapeOutsideConfig {
  // Last-write-wins cascade — identical to every other engine Extractor.
  return { value: foldLast(properties, 'ShapeOutside', shapeValueToCss) };
}
