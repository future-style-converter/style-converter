// LightingColorExtractor.ts — folds IR `LightingColor` values via shared colorOrKeyword.
import { foldLast, colorOrKeyword, type IRPropertyLike } from '../_phase10_shared';
import type { LightingColorConfig } from './LightingColorConfig';
export function extractLightingColor(properties: IRPropertyLike[]): LightingColorConfig {
  return { value: foldLast(properties, 'LightingColor', colorOrKeyword) };
}
