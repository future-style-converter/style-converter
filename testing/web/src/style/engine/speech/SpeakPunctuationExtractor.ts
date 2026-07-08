// SpeakPunctuationExtractor.ts — folds IR `SpeakPunctuation` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { SpeakPunctuationConfig } from './SpeakPunctuationConfig';
export function extractSpeakPunctuation(properties: IRPropertyLike[]): SpeakPunctuationConfig {
  return { value: foldLast(properties, 'SpeakPunctuation', keywordOrRaw) };
}
