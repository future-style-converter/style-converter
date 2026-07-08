// StopColorExtractor.ts — folds IR `StopColor` values via shared colorOrKeyword.
import { foldLast, colorOrKeyword, type IRPropertyLike } from '../_phase10_shared';
import type { StopColorConfig } from './StopColorConfig';
export function extractStopColor(properties: IRPropertyLike[]): StopColorConfig {
  return { value: foldLast(properties, 'StopColor', colorOrKeyword) };
}
