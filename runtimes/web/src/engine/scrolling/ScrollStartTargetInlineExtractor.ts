// ScrollStartTargetInlineExtractor.ts — folds IR `ScrollStartTargetInline` values.
// Wire shape (ScrollStartTargetInlinePropertyParser.kt → flattened enum string):
//   data: 'NONE' | 'AUTO'  → kebab-cased by the shared keywordOrRaw helper.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { ScrollStartTargetInlineConfig } from './ScrollStartTargetInlineConfig';
export function extractScrollStartTargetInline(properties: IRPropertyLike[]): ScrollStartTargetInlineConfig {
  return { value: foldLast(properties, 'ScrollStartTargetInline', keywordOrRaw) };
}
