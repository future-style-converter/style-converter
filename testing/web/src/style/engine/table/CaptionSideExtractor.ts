// CaptionSideExtractor.ts — folds IR `CaptionSide` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { CaptionSideConfig } from './CaptionSideConfig';
export function extractCaptionSide(properties: IRPropertyLike[]): CaptionSideConfig {
  return { value: foldLast(properties, 'CaptionSide', keywordOrRaw) };
}
