// ColorRenderingExtractor.ts — folds IR `ColorRendering` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { ColorRenderingConfig } from './ColorRenderingConfig';
export function extractColorRendering(properties: IRPropertyLike[]): ColorRenderingConfig {
  return { value: foldLast(properties, 'ColorRendering', keywordOrRaw) };
}
