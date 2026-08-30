// ContainIntrinsicHeightExtractor.ts — folds IR `ContainIntrinsicHeight` values via the shared
// containIntrinsicCss bridge (wave-48 W5: keywordOrRaw dropped every length
// shape — see _containIntrinsic.ts for the spec + measured case).
import { foldLast, type IRPropertyLike } from '../_phase10_shared';
import { containIntrinsicCss } from './_containIntrinsic';
import type { ContainIntrinsicHeightConfig } from './ContainIntrinsicHeightConfig';
export function extractContainIntrinsicHeight(properties: IRPropertyLike[]): ContainIntrinsicHeightConfig {
  return { value: foldLast(properties, 'ContainIntrinsicHeight', containIntrinsicCss) };
}
