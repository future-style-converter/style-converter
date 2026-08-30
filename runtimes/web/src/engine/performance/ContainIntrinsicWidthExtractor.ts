// ContainIntrinsicWidthExtractor.ts — folds IR `ContainIntrinsicWidth` values via the shared
// containIntrinsicCss bridge (wave-48 W5: keywordOrRaw dropped every length
// shape — see _containIntrinsic.ts for the spec + measured case).
import { foldLast, type IRPropertyLike } from '../_phase10_shared';
import { containIntrinsicCss } from './_containIntrinsic';
import type { ContainIntrinsicWidthConfig } from './ContainIntrinsicWidthConfig';
export function extractContainIntrinsicWidth(properties: IRPropertyLike[]): ContainIntrinsicWidthConfig {
  return { value: foldLast(properties, 'ContainIntrinsicWidth', containIntrinsicCss) };
}
