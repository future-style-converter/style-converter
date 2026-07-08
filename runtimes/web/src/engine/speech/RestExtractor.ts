// RestExtractor.ts — folds IR `Rest` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { RestConfig } from './RestConfig';
export function extractRest(properties: IRPropertyLike[]): RestConfig {
  return { value: foldLast(properties, 'Rest', keywordOrRaw) };
}
