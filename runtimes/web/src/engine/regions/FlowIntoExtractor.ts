// FlowIntoExtractor.ts — folds IR `FlowInto` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { FlowIntoConfig } from './FlowIntoConfig';
export function extractFlowInto(properties: IRPropertyLike[]): FlowIntoConfig {
  return { value: foldLast(properties, 'FlowInto', keywordOrRaw) };
}
