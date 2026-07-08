// StrokeWidthExtractor.ts — folds IR `StrokeWidth` values via shared lengthOrKeyword.
import { foldLast, lengthOrKeyword, type IRPropertyLike } from '../_phase10_shared';
import type { StrokeWidthConfig } from './StrokeWidthConfig';
export function extractStrokeWidth(properties: IRPropertyLike[]): StrokeWidthConfig {
  return { value: foldLast(properties, 'StrokeWidth', lengthOrKeyword) };
}
