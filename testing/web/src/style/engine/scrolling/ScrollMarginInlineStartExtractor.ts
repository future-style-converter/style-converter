// ScrollMarginInlineStartExtractor.ts — folds IR `ScrollMarginInlineStart` values via shared lengthOrKeyword.
import { foldLast, lengthOrKeyword, type IRPropertyLike } from '../_phase10_shared';
import type { ScrollMarginInlineStartConfig } from './ScrollMarginInlineStartConfig';
export function extractScrollMarginInlineStart(properties: IRPropertyLike[]): ScrollMarginInlineStartConfig {
  return { value: foldLast(properties, 'ScrollMarginInlineStart', lengthOrKeyword) };
}
