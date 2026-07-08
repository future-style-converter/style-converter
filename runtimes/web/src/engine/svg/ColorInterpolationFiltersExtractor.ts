// ColorInterpolationFiltersExtractor.ts — folds IR `ColorInterpolationFilters` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { ColorInterpolationFiltersConfig } from './ColorInterpolationFiltersConfig';
export function extractColorInterpolationFilters(properties: IRPropertyLike[]): ColorInterpolationFiltersConfig {
  return { value: foldLast(properties, 'ColorInterpolationFilters', keywordOrRaw) };
}
