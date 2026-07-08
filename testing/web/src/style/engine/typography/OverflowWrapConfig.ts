// OverflowWrapConfig.ts — typed config for the CSS `overflowWrap` property.
// Family: keyword.  Mirrors the IR shape emitted by the Kotlin parser at
// src/main/kotlin/app/parsing/css/properties/longhands/typography/OverflowWrapPropertyParser.kt.

// CSS value carried verbatim — all typography properties map 1:1 to native CSS
// on the web, so the config just holds a ready-to-emit string.  Undefined means
// "property absent" (last-write-wins upstream decides when to overwrite).
export interface OverflowWrapConfig {
  value?: string | number;                                           // serialised CSS value
}

// IR property type this module recognises.  Exported so the registry / tests
// can assert the exact string without magic literals.
export const OVERFLOW_WRAP_PROPERTY_TYPE = 'OverflowWrap' as const;
export type OverflowWrapPropertyType = typeof OVERFLOW_WRAP_PROPERTY_TYPE;
