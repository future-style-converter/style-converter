// ScrollPaddingInlineExtractor.ts — folds IR `ScrollPaddingInline` values via shared lengthOrKeyword.
import { foldLast, lengthOrKeyword, type IRPropertyLike } from '../_phase10_shared';
import type { ScrollPaddingInlineConfig } from './ScrollPaddingInlineConfig';
export function extractScrollPaddingInline(properties: IRPropertyLike[]): ScrollPaddingInlineConfig {
  return { value: foldLast(properties, 'ScrollPaddingInline', lengthOrKeyword) };
}
