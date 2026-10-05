// AllExtractor.ts — folds `All` IR strings into a kebab-case CSS keyword.
// The LAST keyword entry wins (foldLast) — the same "last `all` governs" rule
// (css-cascade-4 §6.4) applyAllReset uses to pick its split index.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import { ALL_PROPERTY_TYPE, type AllConfig } from './AllConfig';
export function extractAll(properties: IRPropertyLike[]): AllConfig {
  // `INITIAL` → 'initial', `REVERT_LAYER` → 'revert-layer' (kebab fold).
  return { value: foldLast(properties, ALL_PROPERTY_TYPE, keywordOrRaw) };
}
