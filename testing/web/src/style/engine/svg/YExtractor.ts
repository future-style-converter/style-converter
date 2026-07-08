// YExtractor.ts — folds IR `Y` values via shared lengthOrKeyword.
import { foldLast, lengthOrKeyword, type IRPropertyLike } from '../_phase10_shared';
import type { YConfig } from './YConfig';
export function extractY(properties: IRPropertyLike[]): YConfig {
  return { value: foldLast(properties, 'Y', lengthOrKeyword) };
}
