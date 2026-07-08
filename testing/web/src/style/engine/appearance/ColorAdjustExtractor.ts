// ColorAdjustExtractor.ts — folds IR `ColorAdjust` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { ColorAdjustConfig } from './ColorAdjustConfig';
export function extractColorAdjust(properties: IRPropertyLike[]): ColorAdjustConfig {
  return { value: foldLast(properties, 'ColorAdjust', keywordOrRaw) };
}
