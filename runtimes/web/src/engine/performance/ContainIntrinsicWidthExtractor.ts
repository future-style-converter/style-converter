// ContainIntrinsicWidthExtractor.ts — folds IR `ContainIntrinsicWidth` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { ContainIntrinsicWidthConfig } from './ContainIntrinsicWidthConfig';
export function extractContainIntrinsicWidth(properties: IRPropertyLike[]): ContainIntrinsicWidthConfig {
  return { value: foldLast(properties, 'ContainIntrinsicWidth', keywordOrRaw) };
}
