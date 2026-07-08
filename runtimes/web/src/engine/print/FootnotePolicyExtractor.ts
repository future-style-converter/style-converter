// FootnotePolicyExtractor.ts — folds IR `FootnotePolicy` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { FootnotePolicyConfig } from './FootnotePolicyConfig';
export function extractFootnotePolicy(properties: IRPropertyLike[]): FootnotePolicyConfig {
  return { value: foldLast(properties, 'FootnotePolicy', keywordOrRaw) };
}
