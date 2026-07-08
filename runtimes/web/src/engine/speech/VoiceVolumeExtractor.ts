// VoiceVolumeExtractor.ts — folds IR `VoiceVolume` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { VoiceVolumeConfig } from './VoiceVolumeConfig';
export function extractVoiceVolume(properties: IRPropertyLike[]): VoiceVolumeConfig {
  return { value: foldLast(properties, 'VoiceVolume', keywordOrRaw) };
}
