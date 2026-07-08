// PauseAfterExtractor.ts — folds IR `PauseAfter` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { PauseAfterConfig } from './PauseAfterConfig';
export function extractPauseAfter(properties: IRPropertyLike[]): PauseAfterConfig {
  return { value: foldLast(properties, 'PauseAfter', keywordOrRaw) };
}
