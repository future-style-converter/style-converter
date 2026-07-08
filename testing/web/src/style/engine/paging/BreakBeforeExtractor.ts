// BreakBeforeExtractor.ts — folds IR `BreakBefore` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { BreakBeforeConfig } from './BreakBeforeConfig';
export function extractBreakBefore(properties: IRPropertyLike[]): BreakBeforeConfig {
  return { value: foldLast(properties, 'BreakBefore', keywordOrRaw) };
}
