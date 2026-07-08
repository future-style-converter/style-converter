// ImageRenderingExtractor.ts — folds IR `ImageRendering` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { ImageRenderingConfig } from './ImageRenderingConfig';
export function extractImageRendering(properties: IRPropertyLike[]): ImageRenderingConfig {
  return { value: foldLast(properties, 'ImageRendering', keywordOrRaw) };
}
