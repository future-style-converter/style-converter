// MarkerMidExtractor.ts — folds IR `MarkerMid` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { MarkerMidConfig } from './MarkerMidConfig';
export function extractMarkerMid(properties: IRPropertyLike[]): MarkerMidConfig {
  return { value: foldLast(properties, 'MarkerMid', keywordOrRaw) };
}
