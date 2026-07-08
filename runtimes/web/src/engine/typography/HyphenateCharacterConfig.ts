// HyphenateCharacterConfig.ts — typed config for the CSS `hyphenateCharacter` property.
// Family: hyphenate-character.  Mirrors the IR shape emitted by the Kotlin parser at
// src/main/kotlin/app/parsing/css/properties/longhands/typography/HyphenateCharacterPropertyParser.kt.

// CSS value carried verbatim — all typography properties map 1:1 to native CSS
// on the web, so the config just holds a ready-to-emit string.  Undefined means
// "property absent" (last-write-wins upstream decides when to overwrite).
export interface HyphenateCharacterConfig {
  value?: string | number;                                           // serialised CSS value
}

// IR property type this module recognises.  Exported so the registry / tests
// can assert the exact string without magic literals.
export const HYPHENATE_CHARACTER_PROPERTY_TYPE = 'HyphenateCharacter' as const;
export type HyphenateCharacterPropertyType = typeof HYPHENATE_CHARACTER_PROPERTY_TYPE;
