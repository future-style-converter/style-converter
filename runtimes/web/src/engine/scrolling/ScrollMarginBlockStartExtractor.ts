// ScrollMarginBlockStartExtractor.ts — folds IR `ScrollMarginBlockStart` values via shared lengthOrKeyword.
import { foldLast, lengthOrKeyword, type IRPropertyLike } from '../_phase10_shared';
import type { ScrollMarginBlockStartConfig } from './ScrollMarginBlockStartConfig';
export function extractScrollMarginBlockStart(properties: IRPropertyLike[]): ScrollMarginBlockStartConfig {
  return { value: foldLast(properties, 'ScrollMarginBlockStart', lengthOrKeyword) };
}
