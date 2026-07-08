// StressExtractor.ts — folds IR `Stress` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { StressConfig } from './StressConfig';
export function extractStress(properties: IRPropertyLike[]): StressConfig {
  return { value: foldLast(properties, 'Stress', keywordOrRaw) };
}
