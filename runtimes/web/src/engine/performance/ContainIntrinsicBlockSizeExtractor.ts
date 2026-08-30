// ContainIntrinsicBlockSizeExtractor.ts — folds IR `ContainIntrinsicBlockSize` values via the shared
// containIntrinsicCss bridge (wave-48 W5: keywordOrRaw dropped every length
// shape — see _containIntrinsic.ts for the spec + measured case).
import { foldLast, type IRPropertyLike } from '../_phase10_shared';
import { containIntrinsicCss } from './_containIntrinsic';
import type { ContainIntrinsicBlockSizeConfig } from './ContainIntrinsicBlockSizeConfig';
export function extractContainIntrinsicBlockSize(properties: IRPropertyLike[]): ContainIntrinsicBlockSizeConfig {
  return { value: foldLast(properties, 'ContainIntrinsicBlockSize', containIntrinsicCss) };
}
