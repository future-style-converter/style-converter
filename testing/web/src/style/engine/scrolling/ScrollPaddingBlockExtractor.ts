// ScrollPaddingBlockExtractor.ts — folds IR `ScrollPaddingBlock` values via shared lengthOrKeyword.
import { foldLast, lengthOrKeyword, type IRPropertyLike } from '../_phase10_shared';
import type { ScrollPaddingBlockConfig } from './ScrollPaddingBlockConfig';
export function extractScrollPaddingBlock(properties: IRPropertyLike[]): ScrollPaddingBlockConfig {
  return { value: foldLast(properties, 'ScrollPaddingBlock', lengthOrKeyword) };
}
