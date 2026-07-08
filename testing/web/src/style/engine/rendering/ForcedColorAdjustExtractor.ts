// ForcedColorAdjustExtractor.ts — folds IR `ForcedColorAdjust` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { ForcedColorAdjustConfig } from './ForcedColorAdjustConfig';
export function extractForcedColorAdjust(properties: IRPropertyLike[]): ForcedColorAdjustConfig {
  return { value: foldLast(properties, 'ForcedColorAdjust', keywordOrRaw) };
}
