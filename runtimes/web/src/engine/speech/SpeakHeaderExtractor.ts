// SpeakHeaderExtractor.ts — folds IR `SpeakHeader` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { SpeakHeaderConfig } from './SpeakHeaderConfig';
export function extractSpeakHeader(properties: IRPropertyLike[]): SpeakHeaderConfig {
  return { value: foldLast(properties, 'SpeakHeader', keywordOrRaw) };
}
