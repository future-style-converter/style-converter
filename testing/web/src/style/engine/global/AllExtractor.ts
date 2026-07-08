// AllExtractor.ts — folds `All` IR strings into a kebab-case CSS keyword.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import { ALL_PROPERTY_TYPE, type AllConfig } from './AllConfig';
export function extractAll(properties: IRPropertyLike[]): AllConfig {
  return { value: foldLast(properties, ALL_PROPERTY_TYPE, keywordOrRaw) };
}
