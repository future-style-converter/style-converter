// MathStyleExtractor.ts — folds IR `MathStyle` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { MathStyleConfig } from './MathStyleConfig';
export function extractMathStyle(properties: IRPropertyLike[]): MathStyleConfig {
  return { value: foldLast(properties, 'MathStyle', keywordOrRaw) };
}
