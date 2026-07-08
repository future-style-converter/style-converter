// ScrollPaddingBlockEndExtractor.ts — folds IR `ScrollPaddingBlockEnd` values via shared lengthOrKeyword.
import { foldLast, lengthOrKeyword, type IRPropertyLike } from '../_phase10_shared';
import type { ScrollPaddingBlockEndConfig } from './ScrollPaddingBlockEndConfig';
export function extractScrollPaddingBlockEnd(properties: IRPropertyLike[]): ScrollPaddingBlockEndConfig {
  return { value: foldLast(properties, 'ScrollPaddingBlockEnd', lengthOrKeyword) };
}
