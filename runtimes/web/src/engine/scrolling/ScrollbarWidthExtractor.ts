// ScrollbarWidthExtractor.ts — folds IR `ScrollbarWidth` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { ScrollbarWidthConfig } from './ScrollbarWidthConfig';
export function extractScrollbarWidth(properties: IRPropertyLike[]): ScrollbarWidthConfig {
  return { value: foldLast(properties, 'ScrollbarWidth', keywordOrRaw) };
}
