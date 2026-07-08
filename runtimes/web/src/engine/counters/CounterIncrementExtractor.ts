// CounterIncrementExtractor.ts — folds IR `CounterIncrement` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { CounterIncrementConfig } from './CounterIncrementConfig';
export function extractCounterIncrement(properties: IRPropertyLike[]): CounterIncrementConfig {
  return { value: foldLast(properties, 'CounterIncrement', keywordOrRaw) };
}
