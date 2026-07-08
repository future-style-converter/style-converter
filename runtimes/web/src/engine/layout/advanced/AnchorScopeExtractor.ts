// AnchorScopeExtractor.ts — folds IR `AnchorScope` properties into a AnchorScopeConfig.
// Every variant in parsing/css/properties/longhands/layout/**/AnchorScopePropertyParser.kt
// is a single keyword; `kebab()` handles the SHOUTY_SNAKE -> kebab-case rewrite
// and drops unknown shapes (returns undefined) so cascade is safe.

import { AnchorScopeConfig, ANCHORSCOPE_PROPERTY_TYPE } from './AnchorScopeConfig';
import { foldLast, kebab, type IRPropertyLike } from '../_shared';

export function extractAnchorScope(properties: IRPropertyLike[]): AnchorScopeConfig {
  // Last-write-wins cascade — identical to every other engine Extractor.
  return { value: foldLast(properties, ANCHORSCOPE_PROPERTY_TYPE, kebab) };
}
