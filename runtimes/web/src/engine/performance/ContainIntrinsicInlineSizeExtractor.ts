// ContainIntrinsicInlineSizeExtractor.ts — folds IR `ContainIntrinsicInlineSize` values via the shared
// containIntrinsicCss bridge (wave-48 W5: keywordOrRaw dropped every length
// shape — see _containIntrinsic.ts for the spec + measured case).
import { foldLast, type IRPropertyLike } from '../_phase10_shared';
import { containIntrinsicCss } from './_containIntrinsic';
import type { ContainIntrinsicInlineSizeConfig } from './ContainIntrinsicInlineSizeConfig';
export function extractContainIntrinsicInlineSize(properties: IRPropertyLike[]): ContainIntrinsicInlineSizeConfig {
  return { value: foldLast(properties, 'ContainIntrinsicInlineSize', containIntrinsicCss) };
}
