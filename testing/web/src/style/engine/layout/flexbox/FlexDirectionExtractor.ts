// FlexDirectionExtractor.ts — folds IR `FlexDirection` properties into a FlexDirectionConfig.
// Every variant in parsing/css/properties/longhands/layout/**/FlexDirectionPropertyParser.kt
// is a single keyword; `kebab()` handles the SHOUTY_SNAKE -> kebab-case rewrite
// and drops unknown shapes (returns undefined) so cascade is safe.

import { FlexDirectionConfig, FLEXDIRECTION_PROPERTY_TYPE } from './FlexDirectionConfig';
import { foldLast, kebab, type IRPropertyLike } from '../_shared';

export function extractFlexDirection(properties: IRPropertyLike[]): FlexDirectionConfig {
  // Last-write-wins cascade — identical to every other engine Extractor.
  return { value: foldLast(properties, FLEXDIRECTION_PROPERTY_TYPE, kebab) };
}
