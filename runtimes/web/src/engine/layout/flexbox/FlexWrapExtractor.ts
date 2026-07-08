// FlexWrapExtractor.ts — folds IR `FlexWrap` properties into a FlexWrapConfig.
// Every variant in parsing/css/properties/longhands/layout/**/FlexWrapPropertyParser.kt
// is a single keyword; `kebab()` handles the SHOUTY_SNAKE -> kebab-case rewrite
// and drops unknown shapes (returns undefined) so cascade is safe.

import { FlexWrapConfig, FLEXWRAP_PROPERTY_TYPE } from './FlexWrapConfig';
import { foldLast, kebab, type IRPropertyLike } from '../_shared';

export function extractFlexWrap(properties: IRPropertyLike[]): FlexWrapConfig {
  // Last-write-wins cascade — identical to every other engine Extractor.
  return { value: foldLast(properties, FLEXWRAP_PROPERTY_TYPE, kebab) };
}
