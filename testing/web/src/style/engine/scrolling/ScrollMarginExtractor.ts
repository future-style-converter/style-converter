// ScrollMarginExtractor.ts — folds IR `ScrollMargin` values via shared lengthOrKeyword.
import { foldLast, lengthOrKeyword, type IRPropertyLike } from '../_phase10_shared';
import type { ScrollMarginConfig } from './ScrollMarginConfig';
export function extractScrollMargin(properties: IRPropertyLike[]): ScrollMarginConfig {
  return { value: foldLast(properties, 'ScrollMargin', lengthOrKeyword) };
}
