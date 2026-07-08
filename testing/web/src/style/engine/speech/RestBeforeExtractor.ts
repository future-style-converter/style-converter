// RestBeforeExtractor.ts — folds IR `RestBefore` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { RestBeforeConfig } from './RestBeforeConfig';
export function extractRestBefore(properties: IRPropertyLike[]): RestBeforeConfig {
  return { value: foldLast(properties, 'RestBefore', keywordOrRaw) };
}
