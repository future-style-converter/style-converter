// CyExtractor.ts — folds IR `Cy` values via shared lengthOrKeyword.
import { foldLast, lengthOrKeyword, type IRPropertyLike } from '../_phase10_shared';
import type { CyConfig } from './CyConfig';
export function extractCy(properties: IRPropertyLike[]): CyConfig {
  return { value: foldLast(properties, 'Cy', lengthOrKeyword) };
}
