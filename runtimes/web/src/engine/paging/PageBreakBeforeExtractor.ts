// PageBreakBeforeExtractor.ts — folds IR `PageBreakBefore` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { PageBreakBeforeConfig } from './PageBreakBeforeConfig';
export function extractPageBreakBefore(properties: IRPropertyLike[]): PageBreakBeforeConfig {
  return { value: foldLast(properties, 'PageBreakBefore', keywordOrRaw) };
}
