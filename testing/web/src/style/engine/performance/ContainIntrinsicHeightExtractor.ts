// ContainIntrinsicHeightExtractor.ts — folds IR `ContainIntrinsicHeight` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { ContainIntrinsicHeightConfig } from './ContainIntrinsicHeightConfig';
export function extractContainIntrinsicHeight(properties: IRPropertyLike[]): ContainIntrinsicHeightConfig {
  return { value: foldLast(properties, 'ContainIntrinsicHeight', keywordOrRaw) };
}
