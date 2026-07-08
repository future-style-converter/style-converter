// CueAfterExtractor.ts — folds IR `CueAfter` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { CueAfterConfig } from './CueAfterConfig';
export function extractCueAfter(properties: IRPropertyLike[]): CueAfterConfig {
  return { value: foldLast(properties, 'CueAfter', keywordOrRaw) };
}
