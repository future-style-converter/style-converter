// RunningExtractor.ts — folds IR `Running` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { RunningConfig } from './RunningConfig';
export function extractRunning(properties: IRPropertyLike[]): RunningConfig {
  return { value: foldLast(properties, 'Running', keywordOrRaw) };
}
