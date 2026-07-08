// WrapBeforeExtractor.ts — folds IR `WrapBefore` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { WrapBeforeConfig } from './WrapBeforeConfig';
export function extractWrapBefore(properties: IRPropertyLike[]): WrapBeforeConfig {
  return { value: foldLast(properties, 'WrapBefore', keywordOrRaw) };
}
