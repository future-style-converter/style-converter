// ScrollPaddingExtractor.ts — folds IR `ScrollPadding` values via shared lengthOrKeyword.
import { foldLast, lengthOrKeyword, type IRPropertyLike } from '../_phase10_shared';
import type { ScrollPaddingConfig } from './ScrollPaddingConfig';
export function extractScrollPadding(properties: IRPropertyLike[]): ScrollPaddingConfig {
  return { value: foldLast(properties, 'ScrollPadding', lengthOrKeyword) };
}
