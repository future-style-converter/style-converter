// CueExtractor.ts — folds IR `Cue` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { CueConfig } from './CueConfig';
export function extractCue(properties: IRPropertyLike[]): CueConfig {
  return { value: foldLast(properties, 'Cue', keywordOrRaw) };
}
