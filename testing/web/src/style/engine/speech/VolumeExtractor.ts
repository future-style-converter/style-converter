// VolumeExtractor.ts — folds IR `Volume` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { VolumeConfig } from './VolumeConfig';
export function extractVolume(properties: IRPropertyLike[]): VolumeConfig {
  return { value: foldLast(properties, 'Volume', keywordOrRaw) };
}
