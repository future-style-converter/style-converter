// ContainIntrinsicSizeExtractor.ts — folds IR `ContainIntrinsicSize` values via the shared
// containIntrinsicSizeCss bridge (wave-48 W5: keywordOrRaw dropped every
// length shape AND the two-axis {width,height} shorthand wire — see
// _containIntrinsic.ts for the spec + measured case).
import { foldLast, type IRPropertyLike } from '../_phase10_shared';
import { containIntrinsicSizeCss } from './_containIntrinsic';
import type { ContainIntrinsicSizeConfig } from './ContainIntrinsicSizeConfig';
export function extractContainIntrinsicSize(properties: IRPropertyLike[]): ContainIntrinsicSizeConfig {
  return { value: foldLast(properties, 'ContainIntrinsicSize', containIntrinsicSizeCss) };
}
