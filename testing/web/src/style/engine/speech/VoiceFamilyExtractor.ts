// VoiceFamilyExtractor.ts — folds IR `VoiceFamily` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { VoiceFamilyConfig } from './VoiceFamilyConfig';
export function extractVoiceFamily(properties: IRPropertyLike[]): VoiceFamilyConfig {
  return { value: foldLast(properties, 'VoiceFamily', keywordOrRaw) };
}
