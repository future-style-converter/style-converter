// ShapeInsideExtractor.ts — folds IR `ShapeInside` nodes to a CSS string.
// ShapeInsideProperty.kt is the same sealed-variant wire as shape-outside
// (tags `auto` / `none` / `shape`), so it shares the category decoder rather
// than `keywordOrRaw`, which dropped the `{type:'shape', shape:'…'}` variant.
import { foldLast, type IRPropertyLike } from '../_phase10_shared';
import { shapeValueToCss } from './_shared';
import type { ShapeInsideConfig } from './ShapeInsideConfig';
export function extractShapeInside(properties: IRPropertyLike[]): ShapeInsideConfig {
  // Last-write-wins cascade — identical to every other engine Extractor.
  return { value: foldLast(properties, 'ShapeInside', shapeValueToCss) };
}
