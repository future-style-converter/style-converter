// VoiceStressExtractor.ts — folds IR `VoiceStress` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { VoiceStressConfig } from './VoiceStressConfig';
export function extractVoiceStress(properties: IRPropertyLike[]): VoiceStressConfig {
  return { value: foldLast(properties, 'VoiceStress', keywordOrRaw) };
}
