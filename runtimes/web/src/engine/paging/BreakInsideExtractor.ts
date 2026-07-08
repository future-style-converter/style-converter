// BreakInsideExtractor.ts — folds IR `BreakInside` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { BreakInsideConfig } from './BreakInsideConfig';
export function extractBreakInside(properties: IRPropertyLike[]): BreakInsideConfig {
  return { value: foldLast(properties, 'BreakInside', keywordOrRaw) };
}
