// ContainExtractor.ts — folds IR `Contain` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { ContainConfig } from './ContainConfig';
export function extractContain(properties: IRPropertyLike[]): ContainConfig {
  return { value: foldLast(properties, 'Contain', keywordOrRaw) };
}
