// ShapeOutsideExtractor.ts — folds IR `ShapeOutside` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { ShapeOutsideConfig } from './ShapeOutsideConfig';
export function extractShapeOutside(properties: IRPropertyLike[]): ShapeOutsideConfig {
  return { value: foldLast(properties, 'ShapeOutside', keywordOrRaw) };
}
