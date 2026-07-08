// StrokeOpacityExtractor.ts — folds IR `StrokeOpacity` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { StrokeOpacityConfig } from './StrokeOpacityConfig';
export function extractStrokeOpacity(properties: IRPropertyLike[]): StrokeOpacityConfig {
  return { value: foldLast(properties, 'StrokeOpacity', keywordOrRaw) };
}
