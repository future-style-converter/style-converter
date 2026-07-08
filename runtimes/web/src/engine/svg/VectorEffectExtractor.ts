// VectorEffectExtractor.ts — folds IR `VectorEffect` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { VectorEffectConfig } from './VectorEffectConfig';
export function extractVectorEffect(properties: IRPropertyLike[]): VectorEffectConfig {
  return { value: foldLast(properties, 'VectorEffect', keywordOrRaw) };
}
