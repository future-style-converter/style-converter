// PageBreakAfterExtractor.ts — folds IR `PageBreakAfter` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { PageBreakAfterConfig } from './PageBreakAfterConfig';
export function extractPageBreakAfter(properties: IRPropertyLike[]): PageBreakAfterConfig {
  return { value: foldLast(properties, 'PageBreakAfter', keywordOrRaw) };
}
