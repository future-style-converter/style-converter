// ScrollMarginBlockExtractor.ts — folds IR `ScrollMarginBlock` values via shared lengthOrKeyword.
import { foldLast, lengthOrKeyword, type IRPropertyLike } from '../_phase10_shared';
import type { ScrollMarginBlockConfig } from './ScrollMarginBlockConfig';
export function extractScrollMarginBlock(properties: IRPropertyLike[]): ScrollMarginBlockConfig {
  return { value: foldLast(properties, 'ScrollMarginBlock', lengthOrKeyword) };
}
