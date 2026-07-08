// ScrollbarColorExtractor.ts — folds IR `ScrollbarColor` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { ScrollbarColorConfig } from './ScrollbarColorConfig';
export function extractScrollbarColor(properties: IRPropertyLike[]): ScrollbarColorConfig {
  return { value: foldLast(properties, 'ScrollbarColor', keywordOrRaw) };
}
