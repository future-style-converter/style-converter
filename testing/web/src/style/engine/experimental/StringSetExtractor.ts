// StringSetExtractor.ts — folds IR `StringSet` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { StringSetConfig } from './StringSetConfig';
export function extractStringSet(properties: IRPropertyLike[]): StringSetConfig {
  return { value: foldLast(properties, 'StringSet', keywordOrRaw) };
}
