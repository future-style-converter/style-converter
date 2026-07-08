// ColumnSpanExtractor.ts — folds IR `ColumnSpan` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { ColumnSpanConfig } from './ColumnSpanConfig';
export function extractColumnSpan(properties: IRPropertyLike[]): ColumnSpanConfig {
  return { value: foldLast(properties, 'ColumnSpan', keywordOrRaw) };
}
