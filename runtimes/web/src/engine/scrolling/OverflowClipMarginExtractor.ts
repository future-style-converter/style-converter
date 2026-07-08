// OverflowClipMarginExtractor.ts — folds IR `OverflowClipMargin` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { OverflowClipMarginConfig } from './OverflowClipMarginConfig';
export function extractOverflowClipMargin(properties: IRPropertyLike[]): OverflowClipMarginConfig {
  return { value: foldLast(properties, 'OverflowClipMargin', keywordOrRaw) };
}
