// VoicePitchExtractor.ts — folds IR `VoicePitch` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { VoicePitchConfig } from './VoicePitchConfig';
export function extractVoicePitch(properties: IRPropertyLike[]): VoicePitchConfig {
  return { value: foldLast(properties, 'VoicePitch', keywordOrRaw) };
}
