// ScrollMarginRightExtractor.ts — folds IR `ScrollMarginRight` values via shared lengthOrKeyword.
import { foldLast, lengthOrKeyword, type IRPropertyLike } from '../_phase10_shared';
import type { ScrollMarginRightConfig } from './ScrollMarginRightConfig';
export function extractScrollMarginRight(properties: IRPropertyLike[]): ScrollMarginRightConfig {
  return { value: foldLast(properties, 'ScrollMarginRight', lengthOrKeyword) };
}
