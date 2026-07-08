// LeaderExtractor.ts — folds IR `Leader` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { LeaderConfig } from './LeaderConfig';
export function extractLeader(properties: IRPropertyLike[]): LeaderConfig {
  return { value: foldLast(properties, 'Leader', keywordOrRaw) };
}
