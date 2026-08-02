// RowRuleStyleExtractor.ts — folds IR `RowRuleStyle` values via shared keywordOrRaw.
// row-axis gap-decoration <line-style>; IR data is the bare SHOUTY enum (SOLID -> solid).
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { RowRuleStyleConfig } from './RowRuleStyleConfig';
export function extractRowRuleStyle(properties: IRPropertyLike[]): RowRuleStyleConfig {
  // foldLast = last-write-wins cascade, identical to the ColumnRule* twins.
  return { value: foldLast(properties, 'RowRuleStyle', keywordOrRaw) };
}
