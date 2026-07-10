// ScrollStartTargetBlockExtractor.ts — folds IR `ScrollStartTargetBlock` values.
// Wire shape (ScrollStartTargetBlockPropertyParser.kt → flattened enum string):
//   data: 'NONE' | 'AUTO'  → kebab-cased by the shared keywordOrRaw helper.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { ScrollStartTargetBlockConfig } from './ScrollStartTargetBlockConfig';
export function extractScrollStartTargetBlock(properties: IRPropertyLike[]): ScrollStartTargetBlockConfig {
  return { value: foldLast(properties, 'ScrollStartTargetBlock', keywordOrRaw) };
}
