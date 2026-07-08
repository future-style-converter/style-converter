// ScrollPaddingInlineStartExtractor.ts — folds IR `ScrollPaddingInlineStart` values via shared lengthOrKeyword.
import { foldLast, lengthOrKeyword, type IRPropertyLike } from '../_phase10_shared';
import type { ScrollPaddingInlineStartConfig } from './ScrollPaddingInlineStartConfig';
export function extractScrollPaddingInlineStart(properties: IRPropertyLike[]): ScrollPaddingInlineStartConfig {
  return { value: foldLast(properties, 'ScrollPaddingInlineStart', lengthOrKeyword) };
}
