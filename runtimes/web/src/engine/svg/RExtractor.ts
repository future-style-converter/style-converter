// RExtractor.ts — folds IR `R` values via shared lengthOrKeyword.
import { foldLast, lengthOrKeyword, type IRPropertyLike } from '../_phase10_shared';
import type { RConfig } from './RConfig';
export function extractR(properties: IRPropertyLike[]): RConfig {
  return { value: foldLast(properties, 'R', lengthOrKeyword) };
}
