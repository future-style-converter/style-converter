// ScrollbarGutterExtractor.ts — folds IR `ScrollbarGutter` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { ScrollbarGutterConfig } from './ScrollbarGutterConfig';
export function extractScrollbarGutter(properties: IRPropertyLike[]): ScrollbarGutterConfig {
  return { value: foldLast(properties, 'ScrollbarGutter', keywordOrRaw) };
}
