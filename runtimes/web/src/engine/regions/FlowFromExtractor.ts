// FlowFromExtractor.ts — folds IR `FlowFrom` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { FlowFromConfig } from './FlowFromConfig';
export function extractFlowFrom(properties: IRPropertyLike[]): FlowFromConfig {
  return { value: foldLast(properties, 'FlowFrom', keywordOrRaw) };
}
