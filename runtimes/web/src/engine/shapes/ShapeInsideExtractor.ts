// ShapeInsideExtractor.ts — folds IR `ShapeInside` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { ShapeInsideConfig } from './ShapeInsideConfig';
export function extractShapeInside(properties: IRPropertyLike[]): ShapeInsideConfig {
  return { value: foldLast(properties, 'ShapeInside', keywordOrRaw) };
}
