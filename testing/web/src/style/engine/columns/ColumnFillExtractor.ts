// ColumnFillExtractor.ts — folds IR `ColumnFill` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { ColumnFillConfig } from './ColumnFillConfig';
export function extractColumnFill(properties: IRPropertyLike[]): ColumnFillConfig {
  return { value: foldLast(properties, 'ColumnFill', keywordOrRaw) };
}
