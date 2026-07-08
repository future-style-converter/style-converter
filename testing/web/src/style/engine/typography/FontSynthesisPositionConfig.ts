// FontSynthesisPositionConfig.ts — typed config for the CSS `fontSynthesisPosition` property.
// Family: keyword.  Mirrors the IR shape emitted by the Kotlin parser at
// src/main/kotlin/app/parsing/css/properties/longhands/typography/FontSynthesisPositionPropertyParser.kt.

// CSS value carried verbatim — all typography properties map 1:1 to native CSS
// on the web, so the config just holds a ready-to-emit string.  Undefined means
// "property absent" (last-write-wins upstream decides when to overwrite).
export interface FontSynthesisPositionConfig {
  value?: string | number;                                           // serialised CSS value
}

// IR property type this module recognises.  Exported so the registry / tests
// can assert the exact string without magic literals.
export const FONT_SYNTHESIS_POSITION_PROPERTY_TYPE = 'FontSynthesisPosition' as const;
export type FontSynthesisPositionPropertyType = typeof FONT_SYNTHESIS_POSITION_PROPERTY_TYPE;
