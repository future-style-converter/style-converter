// BufferedRenderingExtractor.ts — folds IR `BufferedRendering` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { BufferedRenderingConfig } from './BufferedRenderingConfig';
export function extractBufferedRendering(properties: IRPropertyLike[]): BufferedRenderingConfig {
  return { value: foldLast(properties, 'BufferedRendering', keywordOrRaw) };
}
