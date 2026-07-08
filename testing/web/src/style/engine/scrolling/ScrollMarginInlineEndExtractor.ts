// ScrollMarginInlineEndExtractor.ts — folds IR `ScrollMarginInlineEnd` values via shared lengthOrKeyword.
import { foldLast, lengthOrKeyword, type IRPropertyLike } from '../_phase10_shared';
import type { ScrollMarginInlineEndConfig } from './ScrollMarginInlineEndConfig';
export function extractScrollMarginInlineEnd(properties: IRPropertyLike[]): ScrollMarginInlineEndConfig {
  return { value: foldLast(properties, 'ScrollMarginInlineEnd', lengthOrKeyword) };
}
