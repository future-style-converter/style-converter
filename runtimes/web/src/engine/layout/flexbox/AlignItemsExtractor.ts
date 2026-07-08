// AlignItemsExtractor.ts — folds IR `AlignItems` properties into a AlignItemsConfig.
// Every variant in parsing/css/properties/longhands/layout/**/AlignItemsPropertyParser.kt
// is a single keyword; `kebab()` handles the SHOUTY_SNAKE -> kebab-case rewrite
// and drops unknown shapes (returns undefined) so cascade is safe.

import { AlignItemsConfig, ALIGNITEMS_PROPERTY_TYPE } from './AlignItemsConfig';
import { foldLast, kebab, type IRPropertyLike } from '../_shared';

export function extractAlignItems(properties: IRPropertyLike[]): AlignItemsConfig {
  // Last-write-wins cascade — identical to every other engine Extractor.
  return { value: foldLast(properties, ALIGNITEMS_PROPERTY_TYPE, kebab) };
}
