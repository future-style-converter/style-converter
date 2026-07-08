// ScrollPaddingBlockStartExtractor.ts — folds IR `ScrollPaddingBlockStart` values via shared lengthOrKeyword.
import { foldLast, lengthOrKeyword, type IRPropertyLike } from '../_phase10_shared';
import type { ScrollPaddingBlockStartConfig } from './ScrollPaddingBlockStartConfig';
export function extractScrollPaddingBlockStart(properties: IRPropertyLike[]): ScrollPaddingBlockStartConfig {
  return { value: foldLast(properties, 'ScrollPaddingBlockStart', lengthOrKeyword) };
}
