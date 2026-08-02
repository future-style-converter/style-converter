// ColumnRuleInsetExtractor.ts — folds IR `ColumnRuleInset` values via shared lengthOrKeyword.
// column-axis end inset; IR data is a bare length object {px:N} (negatives allowed).
import { foldLast, lengthOrKeyword, type IRPropertyLike } from '../_phase10_shared';
import type { ColumnRuleInsetConfig } from './ColumnRuleInsetConfig';
export function extractColumnRuleInset(properties: IRPropertyLike[]): ColumnRuleInsetConfig {
  // foldLast = last-write-wins cascade, identical to the ColumnRule* twins.
  return { value: foldLast(properties, 'ColumnRuleInset', lengthOrKeyword) };
}
