// ColorInterpolationExtractor.ts — folds IR `ColorInterpolation` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { ColorInterpolationConfig } from './ColorInterpolationConfig';
export function extractColorInterpolation(properties: IRPropertyLike[]): ColorInterpolationConfig {
  return { value: foldLast(properties, 'ColorInterpolation', keywordOrRaw) };
}
