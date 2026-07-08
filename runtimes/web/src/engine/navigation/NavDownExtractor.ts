// NavDownExtractor.ts — folds IR `NavDown` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { NavDownConfig } from './NavDownConfig';
export function extractNavDown(properties: IRPropertyLike[]): NavDownConfig {
  return { value: foldLast(properties, 'NavDown', keywordOrRaw) };
}
