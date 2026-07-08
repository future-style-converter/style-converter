// BlockStepRoundExtractor.ts — folds IR `BlockStepRound` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { BlockStepRoundConfig } from './BlockStepRoundConfig';
export function extractBlockStepRound(properties: IRPropertyLike[]): BlockStepRoundConfig {
  return { value: foldLast(properties, 'BlockStepRound', keywordOrRaw) };
}
