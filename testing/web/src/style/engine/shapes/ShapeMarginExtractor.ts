// ShapeMarginExtractor.ts — folds IR `ShapeMargin` values via shared lengthOrKeyword.
import { foldLast, lengthOrKeyword, type IRPropertyLike } from '../_phase10_shared';
import type { ShapeMarginConfig } from './ShapeMarginConfig';
export function extractShapeMargin(properties: IRPropertyLike[]): ShapeMarginConfig {
  return { value: foldLast(properties, 'ShapeMargin', lengthOrKeyword) };
}
