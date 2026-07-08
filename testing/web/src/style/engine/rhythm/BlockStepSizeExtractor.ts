// BlockStepSizeExtractor.ts — folds IR `BlockStepSize` values via shared lengthOrKeyword.
import { foldLast, lengthOrKeyword, type IRPropertyLike } from '../_phase10_shared';
import type { BlockStepSizeConfig } from './BlockStepSizeConfig';
export function extractBlockStepSize(properties: IRPropertyLike[]): BlockStepSizeConfig {
  return { value: foldLast(properties, 'BlockStepSize', lengthOrKeyword) };
}
