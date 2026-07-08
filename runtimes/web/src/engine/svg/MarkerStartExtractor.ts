// MarkerStartExtractor.ts — folds IR `MarkerStart` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { MarkerStartConfig } from './MarkerStartConfig';
export function extractMarkerStart(properties: IRPropertyLike[]): MarkerStartConfig {
  return { value: foldLast(properties, 'MarkerStart', keywordOrRaw) };
}
