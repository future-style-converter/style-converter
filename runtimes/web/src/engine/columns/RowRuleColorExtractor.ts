// RowRuleColorExtractor.ts — folds IR `RowRuleColor` values via shared colorOrKeyword.
// row-axis gap-decoration colour; IR data is the shared {srgb,original} colour envelope.
import { foldLast, colorOrKeyword, type IRPropertyLike } from '../_phase10_shared';
import type { RowRuleColorConfig } from './RowRuleColorConfig';
export function extractRowRuleColor(properties: IRPropertyLike[]): RowRuleColorConfig {
  // foldLast = last-write-wins cascade, identical to the ColumnRule* twins.
  return { value: foldLast(properties, 'RowRuleColor', colorOrKeyword) };
}
