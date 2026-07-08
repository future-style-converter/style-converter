// InteractivityExtractor.ts — folds IR `Interactivity` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { InteractivityConfig } from './InteractivityConfig';
export function extractInteractivity(properties: IRPropertyLike[]): InteractivityConfig {
  return { value: foldLast(properties, 'Interactivity', keywordOrRaw) };
}
