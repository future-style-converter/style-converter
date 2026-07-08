// SpeakExtractor.ts — folds IR `Speak` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { SpeakConfig } from './SpeakConfig';
export function extractSpeak(properties: IRPropertyLike[]): SpeakConfig {
  return { value: foldLast(properties, 'Speak', keywordOrRaw) };
}
