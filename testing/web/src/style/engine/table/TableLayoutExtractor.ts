// TableLayoutExtractor.ts — folds IR `TableLayout` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { TableLayoutConfig } from './TableLayoutConfig';
export function extractTableLayout(properties: IRPropertyLike[]): TableLayoutConfig {
  return { value: foldLast(properties, 'TableLayout', keywordOrRaw) };
}
