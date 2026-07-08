// PitchExtractor.ts — folds IR `Pitch` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { PitchConfig } from './PitchConfig';
export function extractPitch(properties: IRPropertyLike[]): PitchConfig {
  return { value: foldLast(properties, 'Pitch', keywordOrRaw) };
}
