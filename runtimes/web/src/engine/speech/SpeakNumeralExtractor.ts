// SpeakNumeralExtractor.ts — folds IR `SpeakNumeral` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { SpeakNumeralConfig } from './SpeakNumeralConfig';
export function extractSpeakNumeral(properties: IRPropertyLike[]): SpeakNumeralConfig {
  return { value: foldLast(properties, 'SpeakNumeral', keywordOrRaw) };
}
