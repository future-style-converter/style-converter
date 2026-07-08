// BlockStepExtractor.ts — folds IR `BlockStep` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { BlockStepConfig } from './BlockStepConfig';
export function extractBlockStep(properties: IRPropertyLike[]): BlockStepConfig {
  return { value: foldLast(properties, 'BlockStep', keywordOrRaw) };
}
