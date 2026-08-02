// RuleOverlapExtractor.ts — folds IR `RuleOverlap` values via shared keywordOrRaw.
// cross-axis paint order; COLUMN_OVER_ROW -> column-over-row via the shared kebab() step.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { RuleOverlapConfig } from './RuleOverlapConfig';
export function extractRuleOverlap(properties: IRPropertyLike[]): RuleOverlapConfig {
  // foldLast = last-write-wins cascade, identical to the ColumnRule* twins.
  return { value: foldLast(properties, 'RuleOverlap', keywordOrRaw) };
}
