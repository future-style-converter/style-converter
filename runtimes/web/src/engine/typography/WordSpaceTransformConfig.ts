// WordSpaceTransformConfig.ts — typed config for the CSS `wordSpaceTransform` property.
// Family: keyword.  Mirrors the IR shape emitted by the Kotlin parser at
// src/main/kotlin/app/parsing/css/properties/longhands/typography/WordSpaceTransformPropertyParser.kt.

// CSS value carried verbatim — all typography properties map 1:1 to native CSS
// on the web, so the config just holds a ready-to-emit string.  Undefined means
// "property absent" (last-write-wins upstream decides when to overwrite).
export interface WordSpaceTransformConfig {
  value?: string | number;                                           // serialised CSS value
}

// IR property type this module recognises.  Exported so the registry / tests
// can assert the exact string without magic literals.
export const WORD_SPACE_TRANSFORM_PROPERTY_TYPE = 'WordSpaceTransform' as const;
export type WordSpaceTransformPropertyType = typeof WORD_SPACE_TRANSFORM_PROPERTY_TYPE;
