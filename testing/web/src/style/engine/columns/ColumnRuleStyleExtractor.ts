// ColumnRuleStyleExtractor.ts — folds IR `ColumnRuleStyle` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { ColumnRuleStyleConfig } from './ColumnRuleStyleConfig';
export function extractColumnRuleStyle(properties: IRPropertyLike[]): ColumnRuleStyleConfig {
  return { value: foldLast(properties, 'ColumnRuleStyle', keywordOrRaw) };
}
