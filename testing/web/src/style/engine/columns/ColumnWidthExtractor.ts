// ColumnWidthExtractor.ts — folds IR `ColumnWidth` values via shared lengthOrKeyword.
import { foldLast, lengthOrKeyword, type IRPropertyLike } from '../_phase10_shared';
import type { ColumnWidthConfig } from './ColumnWidthConfig';
export function extractColumnWidth(properties: IRPropertyLike[]): ColumnWidthConfig {
  return { value: foldLast(properties, 'ColumnWidth', lengthOrKeyword) };
}
