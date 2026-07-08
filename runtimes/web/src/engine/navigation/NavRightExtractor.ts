// NavRightExtractor.ts — folds IR `NavRight` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { NavRightConfig } from './NavRightConfig';
export function extractNavRight(properties: IRPropertyLike[]): NavRightConfig {
  return { value: foldLast(properties, 'NavRight', keywordOrRaw) };
}
