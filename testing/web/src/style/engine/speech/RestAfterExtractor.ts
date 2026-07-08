// RestAfterExtractor.ts — folds IR `RestAfter` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { RestAfterConfig } from './RestAfterConfig';
export function extractRestAfter(properties: IRPropertyLike[]): RestAfterConfig {
  return { value: foldLast(properties, 'RestAfter', keywordOrRaw) };
}
