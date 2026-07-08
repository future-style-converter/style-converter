// VoiceBalanceExtractor.ts — folds IR `VoiceBalance` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { VoiceBalanceConfig } from './VoiceBalanceConfig';
export function extractVoiceBalance(properties: IRPropertyLike[]): VoiceBalanceConfig {
  return { value: foldLast(properties, 'VoiceBalance', keywordOrRaw) };
}
