// TouchActionExtractor.ts — folds IR `TouchAction` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { TouchActionConfig } from './TouchActionConfig';
export function extractTouchAction(properties: IRPropertyLike[]): TouchActionConfig {
  return { value: foldLast(properties, 'TouchAction', keywordOrRaw) };
}
