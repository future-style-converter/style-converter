// XExtractor.ts — folds IR `X` values via shared lengthOrKeyword.
import { foldLast, lengthOrKeyword, type IRPropertyLike } from '../_phase10_shared';
import type { XConfig } from './XConfig';
export function extractX(properties: IRPropertyLike[]): XConfig {
  return { value: foldLast(properties, 'X', lengthOrKeyword) };
}
