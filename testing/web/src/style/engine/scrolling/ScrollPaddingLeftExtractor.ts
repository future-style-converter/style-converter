// ScrollPaddingLeftExtractor.ts — folds IR `ScrollPaddingLeft` values via shared lengthOrKeyword.
import { foldLast, lengthOrKeyword, type IRPropertyLike } from '../_phase10_shared';
import type { ScrollPaddingLeftConfig } from './ScrollPaddingLeftConfig';
export function extractScrollPaddingLeft(properties: IRPropertyLike[]): ScrollPaddingLeftConfig {
  return { value: foldLast(properties, 'ScrollPaddingLeft', lengthOrKeyword) };
}
