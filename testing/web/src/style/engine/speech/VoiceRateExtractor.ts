// VoiceRateExtractor.ts — folds IR `VoiceRate` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { VoiceRateConfig } from './VoiceRateConfig';
export function extractVoiceRate(properties: IRPropertyLike[]): VoiceRateConfig {
  return { value: foldLast(properties, 'VoiceRate', keywordOrRaw) };
}
