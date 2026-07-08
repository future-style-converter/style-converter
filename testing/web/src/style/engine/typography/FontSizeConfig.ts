// FontSizeConfig.ts — typed config for the CSS `fontSize` property.
// Family: font-size.  Mirrors the IR shape emitted by the Kotlin parser at
// src/main/kotlin/app/parsing/css/properties/longhands/typography/FontSizePropertyParser.kt.

// CSS value carried verbatim — all typography properties map 1:1 to native CSS
// on the web, so the config just holds a ready-to-emit string.  Undefined means
// "property absent" (last-write-wins upstream decides when to overwrite).
export interface FontSizeConfig {
  value?: string | number;                                           // serialised CSS value
}

// IR property type this module recognises.  Exported so the registry / tests
// can assert the exact string without magic literals.
export const FONT_SIZE_PROPERTY_TYPE = 'FontSize' as const;
export type FontSizePropertyType = typeof FONT_SIZE_PROPERTY_TYPE;
