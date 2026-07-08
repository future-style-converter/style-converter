// ZoomExtractor.ts — folds IR `Zoom` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { ZoomConfig } from './ZoomConfig';
export function extractZoom(properties: IRPropertyLike[]): ZoomConfig {
  return { value: foldLast(properties, 'Zoom', keywordOrRaw) };
}
