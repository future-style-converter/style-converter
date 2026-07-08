// ScrollMarkerGroupExtractor.ts — folds IR `ScrollMarkerGroup` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { ScrollMarkerGroupConfig } from './ScrollMarkerGroupConfig';
export function extractScrollMarkerGroup(properties: IRPropertyLike[]): ScrollMarkerGroupConfig {
  return { value: foldLast(properties, 'ScrollMarkerGroup', keywordOrRaw) };
}
