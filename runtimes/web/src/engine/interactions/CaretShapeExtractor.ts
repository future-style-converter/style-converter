// CaretShapeExtractor.ts — folds IR `CaretShape` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { CaretShapeConfig } from './CaretShapeConfig';
export function extractCaretShape(properties: IRPropertyLike[]): CaretShapeConfig {
  return { value: foldLast(properties, 'CaretShape', keywordOrRaw) };
}
