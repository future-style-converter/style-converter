// PointerEventsExtractor.ts — folds IR `PointerEvents` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { PointerEventsConfig } from './PointerEventsConfig';
export function extractPointerEvents(properties: IRPropertyLike[]): PointerEventsConfig {
  return { value: foldLast(properties, 'PointerEvents', keywordOrRaw) };
}
