// StrokeMiterlimitExtractor.ts — folds IR `StrokeMiterlimit` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { StrokeMiterlimitConfig } from './StrokeMiterlimitConfig';
export function extractStrokeMiterlimit(properties: IRPropertyLike[]): StrokeMiterlimitConfig {
  return { value: foldLast(properties, 'StrokeMiterlimit', keywordOrRaw) };
}
