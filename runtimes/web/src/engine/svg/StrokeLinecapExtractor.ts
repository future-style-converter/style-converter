// StrokeLinecapExtractor.ts — folds IR `StrokeLinecap` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { StrokeLinecapConfig } from './StrokeLinecapConfig';
export function extractStrokeLinecap(properties: IRPropertyLike[]): StrokeLinecapConfig {
  return { value: foldLast(properties, 'StrokeLinecap', keywordOrRaw) };
}
