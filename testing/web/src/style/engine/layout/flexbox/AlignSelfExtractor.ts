// AlignSelfExtractor.ts — folds IR `AlignSelf` properties into a AlignSelfConfig.
// Every variant in parsing/css/properties/longhands/layout/**/AlignSelfPropertyParser.kt
// is a single keyword; `kebab()` handles the SHOUTY_SNAKE -> kebab-case rewrite
// and drops unknown shapes (returns undefined) so cascade is safe.

import { AlignSelfConfig, ALIGNSELF_PROPERTY_TYPE } from './AlignSelfConfig';
import { foldLast, kebab, type IRPropertyLike } from '../_shared';

export function extractAlignSelf(properties: IRPropertyLike[]): AlignSelfConfig {
  // Last-write-wins cascade — identical to every other engine Extractor.
  return { value: foldLast(properties, ALIGNSELF_PROPERTY_TYPE, kebab) };
}
