// ScrollTargetGroupExtractor.ts — folds IR `ScrollTargetGroup` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { ScrollTargetGroupConfig } from './ScrollTargetGroupConfig';
export function extractScrollTargetGroup(properties: IRPropertyLike[]): ScrollTargetGroupConfig {
  return { value: foldLast(properties, 'ScrollTargetGroup', keywordOrRaw) };
}
