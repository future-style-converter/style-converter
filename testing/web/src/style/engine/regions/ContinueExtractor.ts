// ContinueExtractor.ts — folds IR `Continue` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { ContinueConfig } from './ContinueConfig';
export function extractContinue(properties: IRPropertyLike[]): ContinueConfig {
  return { value: foldLast(properties, 'Continue', keywordOrRaw) };
}
