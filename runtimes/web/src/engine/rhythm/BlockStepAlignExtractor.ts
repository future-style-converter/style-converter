// BlockStepAlignExtractor.ts — folds IR `BlockStepAlign` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { BlockStepAlignConfig } from './BlockStepAlignConfig';
export function extractBlockStepAlign(properties: IRPropertyLike[]): BlockStepAlignConfig {
  return { value: foldLast(properties, 'BlockStepAlign', keywordOrRaw) };
}
