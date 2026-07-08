// StrokeLinejoinExtractor.ts — folds IR `StrokeLinejoin` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { StrokeLinejoinConfig } from './StrokeLinejoinConfig';
export function extractStrokeLinejoin(properties: IRPropertyLike[]): StrokeLinejoinConfig {
  return { value: foldLast(properties, 'StrokeLinejoin', keywordOrRaw) };
}
