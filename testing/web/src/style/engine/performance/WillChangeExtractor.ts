// WillChangeExtractor.ts — folds IR `WillChange` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { WillChangeConfig } from './WillChangeConfig';
export function extractWillChange(properties: IRPropertyLike[]): WillChangeConfig {
  return { value: foldLast(properties, 'WillChange', keywordOrRaw) };
}
