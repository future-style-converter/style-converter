// RowRuleBreakExtractor.ts — folds IR `RowRuleBreak` values via shared keywordOrRaw.
// row-axis segmentation; same three-keyword domain as column-rule-break.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { RowRuleBreakConfig } from './RowRuleBreakConfig';
export function extractRowRuleBreak(properties: IRPropertyLike[]): RowRuleBreakConfig {
  // foldLast = last-write-wins cascade, identical to the ColumnRule* twins.
  return { value: foldLast(properties, 'RowRuleBreak', keywordOrRaw) };
}
