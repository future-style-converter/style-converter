// PaintOrderExtractor.ts — folds IR `PaintOrder` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { PaintOrderConfig } from './PaintOrderConfig';
export function extractPaintOrder(properties: IRPropertyLike[]): PaintOrderConfig {
  return { value: foldLast(properties, 'PaintOrder', keywordOrRaw) };
}
