// RowRuleInsetExtractor.ts — folds IR `RowRuleInset` values via shared lengthOrKeyword.
// row-axis end inset; IR data is a bare length object {px:N} (negatives allowed).
import { foldLast, lengthOrKeyword, type IRPropertyLike } from '../_phase10_shared';
import type { RowRuleInsetConfig } from './RowRuleInsetConfig';
export function extractRowRuleInset(properties: IRPropertyLike[]): RowRuleInsetConfig {
  // foldLast = last-write-wins cascade, identical to the ColumnRule* twins.
  return { value: foldLast(properties, 'RowRuleInset', lengthOrKeyword) };
}
