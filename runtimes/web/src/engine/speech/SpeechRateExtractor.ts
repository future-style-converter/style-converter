// SpeechRateExtractor.ts — folds IR `SpeechRate` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { SpeechRateConfig } from './SpeechRateConfig';
export function extractSpeechRate(properties: IRPropertyLike[]): SpeechRateConfig {
  return { value: foldLast(properties, 'SpeechRate', keywordOrRaw) };
}
