// VoiceRangeExtractor.ts — folds IR `VoiceRange` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { VoiceRangeConfig } from './VoiceRangeConfig';
export function extractVoiceRange(properties: IRPropertyLike[]): VoiceRangeConfig {
  return { value: foldLast(properties, 'VoiceRange', keywordOrRaw) };
}
