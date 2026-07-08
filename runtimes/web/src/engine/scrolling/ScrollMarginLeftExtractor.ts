// ScrollMarginLeftExtractor.ts — folds IR `ScrollMarginLeft` values via shared lengthOrKeyword.
import { foldLast, lengthOrKeyword, type IRPropertyLike } from '../_phase10_shared';
import type { ScrollMarginLeftConfig } from './ScrollMarginLeftConfig';
export function extractScrollMarginLeft(properties: IRPropertyLike[]): ScrollMarginLeftConfig {
  return { value: foldLast(properties, 'ScrollMarginLeft', lengthOrKeyword) };
}
