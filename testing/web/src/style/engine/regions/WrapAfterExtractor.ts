// WrapAfterExtractor.ts — folds IR `WrapAfter` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { WrapAfterConfig } from './WrapAfterConfig';
export function extractWrapAfter(properties: IRPropertyLike[]): WrapAfterConfig {
  return { value: foldLast(properties, 'WrapAfter', keywordOrRaw) };
}
