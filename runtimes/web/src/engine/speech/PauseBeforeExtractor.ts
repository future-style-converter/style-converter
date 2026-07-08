// PauseBeforeExtractor.ts — folds IR `PauseBefore` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { PauseBeforeConfig } from './PauseBeforeConfig';
export function extractPauseBefore(properties: IRPropertyLike[]): PauseBeforeConfig {
  return { value: foldLast(properties, 'PauseBefore', keywordOrRaw) };
}
