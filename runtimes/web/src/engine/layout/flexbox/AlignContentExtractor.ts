// AlignContentExtractor.ts — folds IR `AlignContent` properties into a AlignContentConfig.
// Every variant in parsing/css/properties/longhands/layout/**/AlignContentPropertyParser.kt
// is a single keyword; `kebab()` handles the SHOUTY_SNAKE -> kebab-case rewrite
// and drops unknown shapes (returns undefined) so cascade is safe.

import { AlignContentConfig, ALIGNCONTENT_PROPERTY_TYPE } from './AlignContentConfig';
import { foldLast, kebab, type IRPropertyLike } from '../_shared';

export function extractAlignContent(properties: IRPropertyLike[]): AlignContentConfig {
  // Last-write-wins cascade — identical to every other engine Extractor.
  return { value: foldLast(properties, ALIGNCONTENT_PROPERTY_TYPE, kebab) };
}
