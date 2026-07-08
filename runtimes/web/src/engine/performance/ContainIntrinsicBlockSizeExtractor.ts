// ContainIntrinsicBlockSizeExtractor.ts — folds IR `ContainIntrinsicBlockSize` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { ContainIntrinsicBlockSizeConfig } from './ContainIntrinsicBlockSizeConfig';
export function extractContainIntrinsicBlockSize(properties: IRPropertyLike[]): ContainIntrinsicBlockSizeConfig {
  return { value: foldLast(properties, 'ContainIntrinsicBlockSize', keywordOrRaw) };
}
