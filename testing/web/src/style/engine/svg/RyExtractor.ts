// RyExtractor.ts — folds IR `Ry` values via shared lengthOrKeyword.
import { foldLast, lengthOrKeyword, type IRPropertyLike } from '../_phase10_shared';
import type { RyConfig } from './RyConfig';
export function extractRy(properties: IRPropertyLike[]): RyConfig {
  return { value: foldLast(properties, 'Ry', lengthOrKeyword) };
}
