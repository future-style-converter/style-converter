// ImageResolutionExtractor.ts — folds IR `ImageResolution` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { ImageResolutionConfig } from './ImageResolutionConfig';
export function extractImageResolution(properties: IRPropertyLike[]): ImageResolutionConfig {
  return { value: foldLast(properties, 'ImageResolution', keywordOrRaw) };
}
