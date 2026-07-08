// ShapePaddingExtractor.ts — folds IR `ShapePadding` values via shared lengthOrKeyword.
import { foldLast, lengthOrKeyword, type IRPropertyLike } from '../_phase10_shared';
import type { ShapePaddingConfig } from './ShapePaddingConfig';
export function extractShapePadding(properties: IRPropertyLike[]): ShapePaddingConfig {
  return { value: foldLast(properties, 'ShapePadding', lengthOrKeyword) };
}
