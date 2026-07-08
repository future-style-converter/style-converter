// ScrollSnapStopExtractor.ts — folds IR `ScrollSnapStop` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { ScrollSnapStopConfig } from './ScrollSnapStopConfig';
export function extractScrollSnapStop(properties: IRPropertyLike[]): ScrollSnapStopConfig {
  return { value: foldLast(properties, 'ScrollSnapStop', keywordOrRaw) };
}
