// OverflowAnchorExtractor.ts — folds IR `OverflowAnchor` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { OverflowAnchorConfig } from './OverflowAnchorConfig';
export function extractOverflowAnchor(properties: IRPropertyLike[]): OverflowAnchorConfig {
  return { value: foldLast(properties, 'OverflowAnchor', keywordOrRaw) };
}
