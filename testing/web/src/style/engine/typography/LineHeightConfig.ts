// LineHeightConfig.ts — typed config for the CSS `lineHeight` property.
// Family: line-height.  Mirrors the IR shape emitted by the Kotlin parser at
// src/main/kotlin/app/parsing/css/properties/longhands/typography/LineHeightPropertyParser.kt.

// CSS value carried verbatim — all typography properties map 1:1 to native CSS
// on the web, so the config just holds a ready-to-emit string.  Undefined means
// "property absent" (last-write-wins upstream decides when to overwrite).
export interface LineHeightConfig {
  value?: string | number;                                           // serialised CSS value
}

// IR property type this module recognises.  Exported so the registry / tests
// can assert the exact string without magic literals.
export const LINE_HEIGHT_PROPERTY_TYPE = 'LineHeight' as const;
export type LineHeightPropertyType = typeof LINE_HEIGHT_PROPERTY_TYPE;
