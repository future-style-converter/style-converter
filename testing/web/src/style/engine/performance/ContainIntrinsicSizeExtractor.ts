// ContainIntrinsicSizeExtractor.ts — folds IR `ContainIntrinsicSize` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { ContainIntrinsicSizeConfig } from './ContainIntrinsicSizeConfig';
export function extractContainIntrinsicSize(properties: IRPropertyLike[]): ContainIntrinsicSizeConfig {
  return { value: foldLast(properties, 'ContainIntrinsicSize', keywordOrRaw) };
}
