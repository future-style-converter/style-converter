// MarginBreakExtractor.ts — folds IR `MarginBreak` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { MarginBreakConfig } from './MarginBreakConfig';
export function extractMarginBreak(properties: IRPropertyLike[]): MarginBreakConfig {
  return { value: foldLast(properties, 'MarginBreak', keywordOrRaw) };
}
