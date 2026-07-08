// EmptyCellsExtractor.ts — folds IR `EmptyCells` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { EmptyCellsConfig } from './EmptyCellsConfig';
export function extractEmptyCells(properties: IRPropertyLike[]): EmptyCellsConfig {
  return { value: foldLast(properties, 'EmptyCells', keywordOrRaw) };
}
