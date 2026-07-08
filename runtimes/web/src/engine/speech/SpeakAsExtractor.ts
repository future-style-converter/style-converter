// SpeakAsExtractor.ts — folds IR `SpeakAs` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { SpeakAsConfig } from './SpeakAsConfig';
export function extractSpeakAs(properties: IRPropertyLike[]): SpeakAsConfig {
  return { value: foldLast(properties, 'SpeakAs', keywordOrRaw) };
}
