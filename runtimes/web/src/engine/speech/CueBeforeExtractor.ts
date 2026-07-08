// CueBeforeExtractor.ts — folds IR `CueBefore` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { CueBeforeConfig } from './CueBeforeConfig';
export function extractCueBefore(properties: IRPropertyLike[]): CueBeforeConfig {
  return { value: foldLast(properties, 'CueBefore', keywordOrRaw) };
}
