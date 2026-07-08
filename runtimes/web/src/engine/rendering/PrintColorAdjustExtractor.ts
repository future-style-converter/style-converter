// PrintColorAdjustExtractor.ts — folds IR `PrintColorAdjust` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { PrintColorAdjustConfig } from './PrintColorAdjustConfig';
export function extractPrintColorAdjust(properties: IRPropertyLike[]): PrintColorAdjustConfig {
  return { value: foldLast(properties, 'PrintColorAdjust', keywordOrRaw) };
}
