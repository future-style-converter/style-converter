// MarkerEndExtractor.ts — folds IR `MarkerEnd` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { MarkerEndConfig } from './MarkerEndConfig';
export function extractMarkerEnd(properties: IRPropertyLike[]): MarkerEndConfig {
  return { value: foldLast(properties, 'MarkerEnd', keywordOrRaw) };
}
