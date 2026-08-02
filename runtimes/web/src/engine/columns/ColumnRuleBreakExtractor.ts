// ColumnRuleBreakExtractor.ts — folds IR `ColumnRuleBreak` values via shared keywordOrRaw.
// column-axis segmentation; SPANNING_ITEM -> spanning-item via the shared kebab() step.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { ColumnRuleBreakConfig } from './ColumnRuleBreakConfig';
export function extractColumnRuleBreak(properties: IRPropertyLike[]): ColumnRuleBreakConfig {
  // foldLast = last-write-wins cascade, identical to the ColumnRule* twins.
  return { value: foldLast(properties, 'ColumnRuleBreak', keywordOrRaw) };
}
