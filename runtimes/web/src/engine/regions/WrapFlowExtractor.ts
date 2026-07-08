// WrapFlowExtractor.ts — folds IR `WrapFlow` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { WrapFlowConfig } from './WrapFlowConfig';
export function extractWrapFlow(properties: IRPropertyLike[]): WrapFlowConfig {
  return { value: foldLast(properties, 'WrapFlow', keywordOrRaw) };
}
