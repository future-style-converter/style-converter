// ScrollPaddingInlineEndExtractor.ts — folds IR `ScrollPaddingInlineEnd` values via shared lengthOrKeyword.
import { foldLast, lengthOrKeyword, type IRPropertyLike } from '../_phase10_shared';
import type { ScrollPaddingInlineEndConfig } from './ScrollPaddingInlineEndConfig';
export function extractScrollPaddingInlineEnd(properties: IRPropertyLike[]): ScrollPaddingInlineEndConfig {
  return { value: foldLast(properties, 'ScrollPaddingInlineEnd', lengthOrKeyword) };
}
