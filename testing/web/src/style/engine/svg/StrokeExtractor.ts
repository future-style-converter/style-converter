// StrokeExtractor.ts — folds IR `Stroke` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { StrokeConfig } from './StrokeConfig';
export function extractStroke(properties: IRPropertyLike[]): StrokeConfig {
  return { value: foldLast(properties, 'Stroke', keywordOrRaw) };
}
