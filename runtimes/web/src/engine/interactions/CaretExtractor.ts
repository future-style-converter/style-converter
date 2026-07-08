// CaretExtractor.ts — folds IR `Caret` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { CaretConfig } from './CaretConfig';
export function extractCaret(properties: IRPropertyLike[]): CaretConfig {
  return { value: foldLast(properties, 'Caret', keywordOrRaw) };
}
