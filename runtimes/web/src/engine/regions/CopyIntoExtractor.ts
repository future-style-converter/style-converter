// CopyIntoExtractor.ts — folds IR `CopyInto` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { CopyIntoConfig } from './CopyIntoConfig';
export function extractCopyInto(properties: IRPropertyLike[]): CopyIntoConfig {
  return { value: foldLast(properties, 'CopyInto', keywordOrRaw) };
}
