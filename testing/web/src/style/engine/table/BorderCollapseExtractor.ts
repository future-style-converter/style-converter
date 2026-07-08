// BorderCollapseExtractor.ts — folds IR `BorderCollapse` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { BorderCollapseConfig } from './BorderCollapseConfig';
export function extractBorderCollapse(properties: IRPropertyLike[]): BorderCollapseConfig {
  return { value: foldLast(properties, 'BorderCollapse', keywordOrRaw) };
}
