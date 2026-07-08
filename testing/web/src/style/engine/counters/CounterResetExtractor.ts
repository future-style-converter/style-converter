// CounterResetExtractor.ts — folds IR `CounterReset` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { CounterResetConfig } from './CounterResetConfig';
export function extractCounterReset(properties: IRPropertyLike[]): CounterResetConfig {
  return { value: foldLast(properties, 'CounterReset', keywordOrRaw) };
}
