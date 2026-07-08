// MarkerExtractor.ts — folds IR `Marker` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { MarkerConfig } from './MarkerConfig';
export function extractMarker(properties: IRPropertyLike[]): MarkerConfig {
  return { value: foldLast(properties, 'Marker', keywordOrRaw) };
}
