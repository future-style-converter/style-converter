// PageBreakInsideExtractor.ts — folds IR `PageBreakInside` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { PageBreakInsideConfig } from './PageBreakInsideConfig';
export function extractPageBreakInside(properties: IRPropertyLike[]): PageBreakInsideConfig {
  return { value: foldLast(properties, 'PageBreakInside', keywordOrRaw) };
}
