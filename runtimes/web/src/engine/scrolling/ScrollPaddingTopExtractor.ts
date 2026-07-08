// ScrollPaddingTopExtractor.ts — folds IR `ScrollPaddingTop` values via shared lengthOrKeyword.
import { foldLast, lengthOrKeyword, type IRPropertyLike } from '../_phase10_shared';
import type { ScrollPaddingTopConfig } from './ScrollPaddingTopConfig';
export function extractScrollPaddingTop(properties: IRPropertyLike[]): ScrollPaddingTopConfig {
  return { value: foldLast(properties, 'ScrollPaddingTop', lengthOrKeyword) };
}
