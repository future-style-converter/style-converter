// TextAutospaceConfig.ts — typed config for the CSS `textAutospace` property.
// Family: keyword-list.  Mirrors the IR shape emitted by the Kotlin parser at
// src/main/kotlin/app/parsing/css/properties/longhands/typography/TextAutospacePropertyParser.kt.

// CSS value carried verbatim — all typography properties map 1:1 to native CSS
// on the web, so the config just holds a ready-to-emit string.  Undefined means
// "property absent" (last-write-wins upstream decides when to overwrite).
export interface TextAutospaceConfig {
  value?: string | number;                                           // serialised CSS value
}

// IR property type this module recognises.  Exported so the registry / tests
// can assert the exact string without magic literals.
export const TEXT_AUTOSPACE_PROPERTY_TYPE = 'TextAutospace' as const;
export type TextAutospacePropertyType = typeof TEXT_AUTOSPACE_PROPERTY_TYPE;
