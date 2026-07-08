// StopOpacityExtractor.ts — folds IR `StopOpacity` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { StopOpacityConfig } from './StopOpacityConfig';
export function extractStopOpacity(properties: IRPropertyLike[]): StopOpacityConfig {
  return { value: foldLast(properties, 'StopOpacity', keywordOrRaw) };
}
