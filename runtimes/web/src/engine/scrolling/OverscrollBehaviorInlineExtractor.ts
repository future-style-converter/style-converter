// OverscrollBehaviorInlineExtractor.ts — folds IR `OverscrollBehaviorInline` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { OverscrollBehaviorInlineConfig } from './OverscrollBehaviorInlineConfig';
export function extractOverscrollBehaviorInline(properties: IRPropertyLike[]): OverscrollBehaviorInlineConfig {
  return { value: foldLast(properties, 'OverscrollBehaviorInline', keywordOrRaw) };
}
