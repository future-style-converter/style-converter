// ColumnRuleWidthExtractor.ts — folds IR `ColumnRuleWidth` values via shared lengthOrKeyword.
import { foldLast, lengthOrKeyword, type IRPropertyLike } from '../_phase10_shared';
import type { ColumnRuleWidthConfig } from './ColumnRuleWidthConfig';
export function extractColumnRuleWidth(properties: IRPropertyLike[]): ColumnRuleWidthConfig {
  return { value: foldLast(properties, 'ColumnRuleWidth', lengthOrKeyword) };
}
