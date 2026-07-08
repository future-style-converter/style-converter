// CounterSetExtractor.ts — folds IR `CounterSet` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { CounterSetConfig } from './CounterSetConfig';
export function extractCounterSet(properties: IRPropertyLike[]): CounterSetConfig {
  return { value: foldLast(properties, 'CounterSet', keywordOrRaw) };
}
