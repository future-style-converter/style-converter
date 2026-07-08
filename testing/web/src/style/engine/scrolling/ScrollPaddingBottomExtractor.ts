// ScrollPaddingBottomExtractor.ts — folds IR `ScrollPaddingBottom` values via shared lengthOrKeyword.
import { foldLast, lengthOrKeyword, type IRPropertyLike } from '../_phase10_shared';
import type { ScrollPaddingBottomConfig } from './ScrollPaddingBottomConfig';
export function extractScrollPaddingBottom(properties: IRPropertyLike[]): ScrollPaddingBottomConfig {
  return { value: foldLast(properties, 'ScrollPaddingBottom', lengthOrKeyword) };
}
