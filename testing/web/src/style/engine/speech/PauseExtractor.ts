// PauseExtractor.ts — folds IR `Pause` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { PauseConfig } from './PauseConfig';
export function extractPause(properties: IRPropertyLike[]): PauseConfig {
  return { value: foldLast(properties, 'Pause', keywordOrRaw) };
}
