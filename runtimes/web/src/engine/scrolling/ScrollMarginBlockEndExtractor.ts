// ScrollMarginBlockEndExtractor.ts — folds IR `ScrollMarginBlockEnd` values via shared lengthOrKeyword.
import { foldLast, lengthOrKeyword, type IRPropertyLike } from '../_phase10_shared';
import type { ScrollMarginBlockEndConfig } from './ScrollMarginBlockEndConfig';
export function extractScrollMarginBlockEnd(properties: IRPropertyLike[]): ScrollMarginBlockEndConfig {
  return { value: foldLast(properties, 'ScrollMarginBlockEnd', lengthOrKeyword) };
}
