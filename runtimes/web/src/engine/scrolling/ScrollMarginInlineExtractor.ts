// ScrollMarginInlineExtractor.ts — folds IR `ScrollMarginInline` values via shared lengthOrKeyword.
import { foldLast, lengthOrKeyword, type IRPropertyLike } from '../_phase10_shared';
import type { ScrollMarginInlineConfig } from './ScrollMarginInlineConfig';
export function extractScrollMarginInline(properties: IRPropertyLike[]): ScrollMarginInlineConfig {
  return { value: foldLast(properties, 'ScrollMarginInline', lengthOrKeyword) };
}
