// FillRuleExtractor.ts — folds IR `FillRule` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { FillRuleConfig } from './FillRuleConfig';
export function extractFillRule(properties: IRPropertyLike[]): FillRuleConfig {
  return { value: foldLast(properties, 'FillRule', keywordOrRaw) };
}
