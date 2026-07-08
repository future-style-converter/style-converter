// RxExtractor.ts — folds IR `Rx` values via shared lengthOrKeyword.
import { foldLast, lengthOrKeyword, type IRPropertyLike } from '../_phase10_shared';
import type { RxConfig } from './RxConfig';
export function extractRx(properties: IRPropertyLike[]): RxConfig {
  return { value: foldLast(properties, 'Rx', lengthOrKeyword) };
}
