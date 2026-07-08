// ScrollPaddingRightExtractor.ts — folds IR `ScrollPaddingRight` values via shared lengthOrKeyword.
import { foldLast, lengthOrKeyword, type IRPropertyLike } from '../_phase10_shared';
import type { ScrollPaddingRightConfig } from './ScrollPaddingRightConfig';
export function extractScrollPaddingRight(properties: IRPropertyLike[]): ScrollPaddingRightConfig {
  return { value: foldLast(properties, 'ScrollPaddingRight', lengthOrKeyword) };
}
