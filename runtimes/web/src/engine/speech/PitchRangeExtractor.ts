// PitchRangeExtractor.ts — folds IR `PitchRange` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { PitchRangeConfig } from './PitchRangeConfig';
export function extractPitchRange(properties: IRPropertyLike[]): PitchRangeConfig {
  return { value: foldLast(properties, 'PitchRange', keywordOrRaw) };
}
