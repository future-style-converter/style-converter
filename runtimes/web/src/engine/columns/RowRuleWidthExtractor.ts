// RowRuleWidthExtractor.ts — folds IR `RowRuleWidth` values via shared lengthOrKeyword.
// row-axis gap-decoration <line-width>; IR data is {type:'length',px:N} or {type:'keyword',value:'THIN'}.
import { foldLast, lengthOrKeyword, type IRPropertyLike } from '../_phase10_shared';
import type { RowRuleWidthConfig } from './RowRuleWidthConfig';
export function extractRowRuleWidth(properties: IRPropertyLike[]): RowRuleWidthConfig {
  // foldLast = last-write-wins cascade, identical to the ColumnRule* twins.
  return { value: foldLast(properties, 'RowRuleWidth', lengthOrKeyword) };
}
