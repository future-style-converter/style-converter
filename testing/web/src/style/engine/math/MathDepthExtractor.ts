// MathDepthExtractor.ts — folds IR `MathDepth` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { MathDepthConfig } from './MathDepthConfig';
export function extractMathDepth(properties: IRPropertyLike[]): MathDepthConfig {
  return { value: foldLast(properties, 'MathDepth', keywordOrRaw) };
}
