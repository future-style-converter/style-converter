// ScrollStartInlineExtractor.ts — folds IR `ScrollStartInline` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { ScrollStartInlineConfig } from './ScrollStartInlineConfig';
export function extractScrollStartInline(properties: IRPropertyLike[]): ScrollStartInlineConfig {
  return { value: foldLast(properties, 'ScrollStartInline', keywordOrRaw) };
}
