// DExtractor.ts — folds IR `D` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { DConfig } from './DConfig';
export function extractD(properties: IRPropertyLike[]): DConfig {
  return { value: foldLast(properties, 'D', keywordOrRaw) };
}
