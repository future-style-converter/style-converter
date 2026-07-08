// FieldSizingExtractor.ts — folds IR `FieldSizing` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { FieldSizingConfig } from './FieldSizingConfig';
export function extractFieldSizing(properties: IRPropertyLike[]): FieldSizingConfig {
  return { value: foldLast(properties, 'FieldSizing', keywordOrRaw) };
}
