// MathShiftExtractor.ts — folds IR `MathShift` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { MathShiftConfig } from './MathShiftConfig';
export function extractMathShift(properties: IRPropertyLike[]): MathShiftConfig {
  return { value: foldLast(properties, 'MathShift', keywordOrRaw) };
}
