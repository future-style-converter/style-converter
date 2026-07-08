// ColumnRuleColorExtractor.ts — folds IR `ColumnRuleColor` values via shared colorOrKeyword.
import { foldLast, colorOrKeyword, type IRPropertyLike } from '../_phase10_shared';
import type { ColumnRuleColorConfig } from './ColumnRuleColorConfig';
export function extractColumnRuleColor(properties: IRPropertyLike[]): ColumnRuleColorConfig {
  return { value: foldLast(properties, 'ColumnRuleColor', colorOrKeyword) };
}
