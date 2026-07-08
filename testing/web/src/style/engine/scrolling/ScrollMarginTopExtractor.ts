// ScrollMarginTopExtractor.ts — folds IR `ScrollMarginTop` values via shared lengthOrKeyword.
import { foldLast, lengthOrKeyword, type IRPropertyLike } from '../_phase10_shared';
import type { ScrollMarginTopConfig } from './ScrollMarginTopConfig';
export function extractScrollMarginTop(properties: IRPropertyLike[]): ScrollMarginTopConfig {
  return { value: foldLast(properties, 'ScrollMarginTop', lengthOrKeyword) };
}
