// ReadingOrderExtractor.ts — folds IR `ReadingOrder` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { ReadingOrderConfig } from './ReadingOrderConfig';
export function extractReadingOrder(properties: IRPropertyLike[]): ReadingOrderConfig {
  return { value: foldLast(properties, 'ReadingOrder', keywordOrRaw) };
}
