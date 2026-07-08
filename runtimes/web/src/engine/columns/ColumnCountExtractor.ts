// ColumnCountExtractor.ts — folds IR `ColumnCount` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { ColumnCountConfig } from './ColumnCountConfig';
export function extractColumnCount(properties: IRPropertyLike[]): ColumnCountConfig {
  return { value: foldLast(properties, 'ColumnCount', keywordOrRaw) };
}
