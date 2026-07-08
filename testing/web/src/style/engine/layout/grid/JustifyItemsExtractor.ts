// JustifyItemsExtractor.ts — folds IR `JustifyItems` properties into a JustifyItemsConfig.
// Every variant in parsing/css/properties/longhands/layout/**/JustifyItemsPropertyParser.kt
// is a single keyword; `kebab()` handles the SHOUTY_SNAKE -> kebab-case rewrite
// and drops unknown shapes (returns undefined) so cascade is safe.

import { JustifyItemsConfig, JUSTIFYITEMS_PROPERTY_TYPE } from './JustifyItemsConfig';
import { foldLast, kebab, type IRPropertyLike } from '../_shared';

export function extractJustifyItems(properties: IRPropertyLike[]): JustifyItemsConfig {
  // Last-write-wins cascade — identical to every other engine Extractor.
  return { value: foldLast(properties, JUSTIFYITEMS_PROPERTY_TYPE, kebab) };
}
