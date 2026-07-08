// ScrollMarginBottomExtractor.ts — folds IR `ScrollMarginBottom` values via shared lengthOrKeyword.
import { foldLast, lengthOrKeyword, type IRPropertyLike } from '../_phase10_shared';
import type { ScrollMarginBottomConfig } from './ScrollMarginBottomConfig';
export function extractScrollMarginBottom(properties: IRPropertyLike[]): ScrollMarginBottomConfig {
  return { value: foldLast(properties, 'ScrollMarginBottom', lengthOrKeyword) };
}
