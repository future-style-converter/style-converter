// VoiceDurationExtractor.ts — folds IR `VoiceDuration` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { VoiceDurationConfig } from './VoiceDurationConfig';
export function extractVoiceDuration(properties: IRPropertyLike[]): VoiceDurationConfig {
  return { value: foldLast(properties, 'VoiceDuration', keywordOrRaw) };
}
