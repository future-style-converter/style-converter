// CxExtractor.ts — folds IR `Cx` values via shared lengthOrKeyword.
import { foldLast, lengthOrKeyword, type IRPropertyLike } from '../_phase10_shared';
import type { CxConfig } from './CxConfig';
export function extractCx(properties: IRPropertyLike[]): CxConfig {
  return { value: foldLast(properties, 'Cx', lengthOrKeyword) };
}
