// BlockStepInsertExtractor.ts — folds IR `BlockStepInsert` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { BlockStepInsertConfig } from './BlockStepInsertConfig';
export function extractBlockStepInsert(properties: IRPropertyLike[]): BlockStepInsertConfig {
  return { value: foldLast(properties, 'BlockStepInsert', keywordOrRaw) };
}
