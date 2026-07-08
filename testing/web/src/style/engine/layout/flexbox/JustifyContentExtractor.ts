// JustifyContentExtractor.ts — folds IR `JustifyContent` properties into a JustifyContentConfig.
// Every variant in parsing/css/properties/longhands/layout/**/JustifyContentPropertyParser.kt
// is a single keyword; `kebab()` handles the SHOUTY_SNAKE -> kebab-case rewrite
// and drops unknown shapes (returns undefined) so cascade is safe.

import { JustifyContentConfig, JUSTIFYCONTENT_PROPERTY_TYPE } from './JustifyContentConfig';
import { foldLast, kebab, type IRPropertyLike } from '../_shared';

export function extractJustifyContent(properties: IRPropertyLike[]): JustifyContentConfig {
  // Last-write-wins cascade — identical to every other engine Extractor.
  return { value: foldLast(properties, JUSTIFYCONTENT_PROPERTY_TYPE, kebab) };
}
