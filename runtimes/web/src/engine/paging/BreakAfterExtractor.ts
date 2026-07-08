// BreakAfterExtractor.ts — folds IR `BreakAfter` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { BreakAfterConfig } from './BreakAfterConfig';
export function extractBreakAfter(properties: IRPropertyLike[]): BreakAfterConfig {
  return { value: foldLast(properties, 'BreakAfter', keywordOrRaw) };
}
