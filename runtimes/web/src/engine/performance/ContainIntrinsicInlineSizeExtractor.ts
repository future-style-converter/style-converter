// ContainIntrinsicInlineSizeExtractor.ts — folds IR `ContainIntrinsicInlineSize` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { ContainIntrinsicInlineSizeConfig } from './ContainIntrinsicInlineSizeConfig';
export function extractContainIntrinsicInlineSize(properties: IRPropertyLike[]): ContainIntrinsicInlineSizeConfig {
  return { value: foldLast(properties, 'ContainIntrinsicInlineSize', keywordOrRaw) };
}
