// NavUpExtractor.ts — folds IR `NavUp` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { NavUpConfig } from './NavUpConfig';
export function extractNavUp(properties: IRPropertyLike[]): NavUpConfig {
  return { value: foldLast(properties, 'NavUp', keywordOrRaw) };
}
