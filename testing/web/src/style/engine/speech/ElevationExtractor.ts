// ElevationExtractor.ts — folds IR `Elevation` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { ElevationConfig } from './ElevationConfig';
export function extractElevation(properties: IRPropertyLike[]): ElevationConfig {
  return { value: foldLast(properties, 'Elevation', keywordOrRaw) };
}
